package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.NftUpdate;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.schemas.wallet.offers.OfferSummary;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.mindlib.data.tuples.Pair;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;


public class DIDUpdateService implements Runnable {
    private final LinkedBlockingQueue<Pair<String, Integer>> updateOffers = new LinkedBlockingQueue<>(10);
    private final WalletAPI walletAPI;
    private final CustomLogger logger;
    private final OkraGameAPI gameApi;

    String uuid = UUID.randomUUID().toString();

    public DIDUpdateService(WalletAPI monWalletApi, OkraGameAPI gameApi, CustomLogger logger) {
        this.walletAPI = monWalletApi;
        this.logger = logger;
        this.gameApi = gameApi;
    }

    public void submit(Pair<String, Integer> offer) {
        updateOffers.add(offer);
    }

    public void run() {

        List<Pair<String, Integer>> updateList = IntStream.range(0, Math.min(20, updateOffers.size()))
                .mapToObj((i) -> updateOffers.poll()).filter(Objects::nonNull).toList();

        logger.logApp(this.getClass(), TLogLevel.INFO, "Starting DIDUpdateJob: " + uuid
                + " for: " + updateList);

        for (var offer : updateList) {
            try {
                if (!walletAPI.checkOfferValidity(offer.first()).data().orElseThrow().valid()) {
                    logger.logApp(this.getClass(), TLogLevel.ERROR, "Invalid offer submitted by PlayerId: " + offer.second() +
                            "Offer: " + offer.first());
                }
                OfferSummary summary = walletAPI.getOfferSummary(offer.first(), true).data().orElseThrow();
                String nftLauncher = summary.offered().keySet().iterator().next();
                if (gameApi.isValidDidLauncher(nftLauncher).data().orElseThrow()) {
                    gameApi.updatePlayerDid(offer.second(), nftLauncher);
                }

            } catch (Exception e) {

                logger.logApp(this.getClass(), TLogLevel.ERROR, "DIDUpdateJob: " + uuid + " | Failed parsing offer for:"
                        + offer + " | Message: " + e.getMessage());
            }
        }
    }
}
