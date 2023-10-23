package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.RewardDispersal;
import io.mindspice.itemserver.Settings;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.schemas.wallet.Addition;
import io.mindspice.jxch.transact.service.mint.MintItem;
import io.mindspice.jxch.transact.service.transaction.TransactionItem;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.mindlib.util.JsonUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


public class RewardService implements Runnable {
    private final OkraGameAPI gameAPI;
    private final OkraNFTAPI nftAPI;
    private final OkraChiaAPI chiaAPI;
    private final CardMintService mintService;
    private final FullNodeAPI nodeAPI;
    private final TokenService okraTokenService;
    private final TokenService outrTokenService;
    private final List<Card> cardList;
    private final TLogger logger;
    private final ThreadLocalRandom rand = ThreadLocalRandom.current();

    public RewardService(OkraGameAPI gameAPI, OkraNFTAPI nftAPI, OkraChiaAPI chiaAPI, FullNodeAPI nodeAPI,
            CardMintService mintService, TokenService okraTokenService, TokenService outrTokenService,
            List<Card> cardList, TLogger logger) {
        this.gameAPI = gameAPI;
        this.nftAPI = nftAPI;
        this.chiaAPI = chiaAPI;
        this.nodeAPI = nodeAPI;
        this.mintService = mintService;
        this.okraTokenService = okraTokenService;
        this.outrTokenService = outrTokenService;
        this.cardList = cardList;
        this.logger = logger;
    }

    private List<Card> getRandomCards(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(i -> cardList.get(rand.nextInt(0, cardList.size())))
                .toList();
    }

    private Runnable getDispersalTask(RewardDispersal dispersal) {
        return () -> {
            try {
                String accountCoin = gameAPI.getAccountCoin(dispersal.playerId()).data().orElseThrow();
                String address = nodeAPI.getNftRecipientAddress(accountCoin).data().orElseThrow();

                if ((dispersal.okraTokens() > Settings.get().okraFlagAmount)
                        || (dispersal.nftDrops() > Settings.get().nftFlagAmount)
                        || (dispersal.outrTokens() > Settings.get().outrFlagAmount)) {
                    gameAPI.addFlaggedDispersal(
                            dispersal.playerId(), dispersal.okraTokens(), dispersal.outrTokens(), dispersal.nftDrops()
                    );
                    logger.log(this.getClass(), TLogLevel.INFO, "Flagged dispersal: Player id: "
                            + dispersal.playerId() + " | Okra Tokens: " + dispersal.okraTokens()
                            + " | Outr Tokens: " + dispersal.outrTokens() + " | NFT drops:" + dispersal.nftDrops());
                }

                if (dispersal.okraTokens() > 0) {
                    okraTokenService.submit(// 1000 mojo per token
                            new TransactionItem(new Addition(address, dispersal.okraTokens() * 1000L))
                    );
                }

                if (dispersal.outrTokens() > 0) {
                    outrTokenService.submit(// 1000 mojo per token
                            new TransactionItem(new Addition(address, dispersal.outrTokens() * 1000L))
                    );

                }
                if (dispersal.nftDrops() > 0) {
                    List<MintItem> cardDrops = getRandomCards(dispersal.nftDrops()).stream()
                            .map(c -> {
                                int edt = nftAPI.getAndIncEdt(Settings.get().currCollection, c.uid()).data().orElseThrow();
                                return new MintItem(
                                        address,
                                        c.metaData().cloneSetEdt(edt),
                                        "drop:" + dispersal.playerId() + ":" + UUID.randomUUID()
                                );
                            }).toList();
                    mintService.submit(cardDrops);
                }
            } catch (Exception e) {
                logger.log(this.getClass(), TLogLevel.FAILED, "Error calculating dispersal for player id:"
                        + dispersal.playerId() + " | OKRA: " + dispersal.okraTokens() + " NFTs: " + dispersal.nftDrops());
            }
        };
    }

    @Override
    public void run() {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var dispersalResponse = gameAPI.getRewardsForDispersal();
            if (dispersalResponse.error() || !dispersalResponse.success()) {
                logger.log(this.getClass(), TLogLevel.ERROR,
                        "Failed fetching rewards for dispersal" + dispersalResponse.error_msg()
                );
                return;
            }

            var resetDailyResponse = gameAPI.resetDailyResults();
            if (resetDailyResponse.error()) {
                logger.log(this.getClass(), TLogLevel.ERROR, "Failed resetting daily results");
            }

            var resetFreeGamesResponse = gameAPI.resetFreeGames();
            if (resetFreeGamesResponse.error()) {
                logger.log(this.getClass(), TLogLevel.ERROR, "Failed resetting free games");
            }

            if (dispersalResponse.data().isEmpty()) {
                logger.log(this.getClass(), TLogLevel.INFO, "No rewards found");
                return;
            }

            try {
                logger.log(this.getClass(), TLogLevel.INFO, "Dispersing Rewards: " + JsonUtils.writeString(dispersalResponse.data().get()));
            } catch (Exception e) {
                logger.log(this.getClass(), TLogLevel.ERROR, "Failed writing dispersals falling back to java string " +
                        "deserialization: " + dispersalResponse.data().get());
            }

            dispersalResponse.data().get().forEach(r -> exec.submit(getDispersalTask(r)));
        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.ERROR, " Error encounter in dispersal service", e);
        }
    }
}
