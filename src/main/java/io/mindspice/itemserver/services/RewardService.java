package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.RewardDispersal;
import io.mindspice.itemserver.Settings;
import io.mindspice.jxch.rpc.schemas.wallet.Addition;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.transaction.TransactionItem;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


public class RewardsService implements Runnable {
    private volatile LocalDateTime nextDispersal;
    private final OkraGameAPI gameAPI;
    private final OkraNFTAPI nftAPI;
    private final OkraChiaAPI chiaAPI;
    private final CardMintService mintService;
    private final TokenService tokenService;
    private final List<Card> cardList;
    private final TLogger logger;
    private final ThreadLocalRandom rand = ThreadLocalRandom.current();

    public RewardsService(OkraGameAPI gameAPI, OkraNFTAPI nftAPI, OkraChiaAPI chiaAPI, CardMintService mintService,
            TokenService tokenService, List<Card> cardList, TLogger logger) {
        this.gameAPI = gameAPI;
        this.nftAPI = nftAPI;
        this.chiaAPI = chiaAPI;
        this.mintService = mintService;
        this.tokenService = tokenService;
        this.cardList = cardList;
        this.logger = logger;

        this.nextDispersal = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1); // Next midnight
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
                String address = chiaAPI.getCoinRecordByName(accountCoin)
                        .data().orElseThrow().coin().puzzleHash();

                if (dispersal.okraTokens() > 0) {
                    tokenService.submit(// 1000 mojo per token
                            new TransactionItem(new Addition(address, dispersal.okraTokens() * 1000L))
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
        // Check if it's time for token dispersal
        if (!LocalDateTime.now().isAfter(nextDispersal)) { return; }

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = gameAPI.getRewardsForDispersal();
            if (response.error() || !response.success() || response.data().isEmpty()) {
                logger.log(this.getClass(), TLogLevel.ERROR,
                        "Failed fetching rewards for dispersal" + response.error_msg()
                );
                return;
            }
            nextDispersal = nextDispersal.plusDays(1);
            response.data().get().forEach(r -> exec.submit(getDispersalTask(r)));
        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.ERROR, " Error encounter in dispersal service", e);
        }
    }
}
