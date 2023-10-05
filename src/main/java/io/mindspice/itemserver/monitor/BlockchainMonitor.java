package io.mindspice.itemserver.monitor;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.AccountDid;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardAndAccountCheck;
import io.mindspice.databaseservice.client.schema.NftUpdate;
import io.mindspice.itemserver.schema.PackPurchase;
import io.mindspice.itemserver.schema.PackType;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.services.PackMint;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.ApiResponse;
import io.mindspice.jxch.rpc.schemas.custom.CatSenderInfo;
import io.mindspice.jxch.rpc.schemas.object.CoinRecord;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.RPCException;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.mindlib.data.tuples.Pair;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;


public class BlockchainMonitor implements Runnable {
    private volatile int nextHeight;
    private final FullNodeAPI nodeAPI;
    private final WalletAPI walletAPI;
    private final OkraChiaAPI chiaAPI;
    private final OkraNFTAPI nftAPI;
    private final MintService mintService;
    private final Map<String, Pair<String, PackType>> assetMap;
    private final List<Card> cardList;
    private final TLogger logger;
    private final Semaphore semaphore = new Semaphore(1);
    // Add autowired reference here to use in this class, but have the rest manual managed

    protected final Supplier<RPCException> chiaExcept =
            () -> new RPCException("Required Chia RPC call returned Optional.empty");

    private final ExecutorService virtualExec = Executors.newVirtualThreadPerTaskExecutor();

    public BlockchainMonitor(
            FullNodeAPI nodeAPI, WalletAPI walletAPI,
            OkraChiaAPI chiaAPI, OkraNFTAPI nftAPI,
            MintService mintService, Map<String,
            Pair<String, PackType>> assetMap,
            List<Card> cardList, int startHeight,
            TLogger logger) {

        nextHeight = startHeight;
        this.nodeAPI = nodeAPI;
        this.walletAPI = walletAPI;
        this.chiaAPI = chiaAPI;
        this.nftAPI = nftAPI;
        this.mintService = mintService;
        this.assetMap = assetMap;
        this.cardList = cardList;
        this.logger = logger;
        logger.log(this.getClass(), TLogLevel.INFO, "Started Blockchain Monitor");
    }

    @Override
    public void run() {
        if (Settings.get().isPaused) { return; }
        if (semaphore.availablePermits() == 0) { return; }
        try {
            semaphore.acquire();
            if (!((nodeAPI.getHeight().data().orElseThrow() - Settings.get().heightBuffer) >= nextHeight)) {
                return;
            }
            long start = System.currentTimeMillis();

            List<CoinRecord> additions = chiaAPI.getCoinRecordsByHeight(nextHeight)
                    .data().orElseThrow(chiaExcept)
                    .additions().stream().filter(a -> !a.coinbase()).toList();

            if (additions.isEmpty()) {
                logger.log(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                        " | Additions: 0" +
                        " | Block scan took: " + (System.currentTimeMillis() - start) + " ms");
                nextHeight++; // Non-atomic inc doesn't matter, non-critical, is volatile to observe when monitoring
                return;
            }

            CompletableFuture<CardAndAccountCheck> cardAndAccountCheck = CompletableFuture.supplyAsync(() -> {
                var cardOrAccountUpdates = nftAPI.checkIfCardOrAccountExists(
                        additions.stream().map(a -> a.coin().parentCoinInfo()).toList()
                );

                if (cardOrAccountUpdates.data().isPresent()) {
                    return cardOrAccountUpdates.data().get();
                }
                return null;
            }, Executors.newVirtualThreadPerTaskExecutor());

            CompletableFuture<List<PackPurchase>> packCheck = CompletableFuture.supplyAsync(() -> {
                List<PackPurchase> packPurchases = null;
                var packRecords = additions.stream().filter(a -> assetMap.containsKey(a.coin().puzzleHash())).toList();
                if (!packRecords.isEmpty()) {
                    packPurchases = new ArrayList<>(packRecords.size());
                    for (var record : packRecords) {
                        int amount = (int) (record.coin().amount() / 1000);
                        try {
                            ApiResponse<CatSenderInfo> catInfoResp = nodeAPI.getCatSenderInfo(record);
                            if (!catInfoResp.success()) {
                                logger.log(this.getClass(), TLogLevel.ERROR, " Failed asset lookup, wrong asset?" +
                                        " | Error: " + catInfoResp.error());
                                continue;
                            }

                            CatSenderInfo catInfo = catInfoResp.data().orElseThrow(chiaExcept);
                            if (!catInfo.assetId().equals(assetMap.get(record.coin().puzzleHash()).first())) {
                                logger.log(this.getClass(), TLogLevel.INFO, "Wrong Asset Received: " + catInfo.assetId()
                                        + " Expected: " + assetMap.get(record.coin().puzzleHash()).first()
                                        + " Amount: " + record.coin().amount());
                                continue;
                            }

                            PackType packType = assetMap.get(record.coin().puzzleHash()).second();
                            for (int i = 0; i < amount; ++i) {
                                String uuid = UUID.randomUUID().toString();
                                logger.log(this.getClass(), TLogLevel.INFO, "Submitted pack purchase " +
                                        " | UUID: " + uuid +
                                        " | Parent Coin: " + record.coin().parentCoinInfo() +
                                        " | Amount(mojos / 1000): " + amount +
                                        " | Asset: " + catInfo.assetId() +
                                        " | Sender Address" + catInfo.senderPuzzleHash()
                                );
                                packPurchases.add(new PackPurchase(catInfo.senderPuzzleHash(), packType, uuid));
                            }
                        } catch (RPCException e) {
                            logger.log(this.getClass(), TLogLevel.FAILED, "Failed asset lookups at height: " + nextHeight +
                                    " | Reason: " + e.getMessage(), e);
                        }
                    }
                    return packPurchases;
                }
                return null;
            }, Executors.newVirtualThreadPerTaskExecutor());

            CardAndAccountCheck cardAndAccountResults = cardAndAccountCheck.get();
            List<PackPurchase> packPurchasesResults = packCheck.get();

            boolean foundChange = false;
            if (cardAndAccountResults != null) {
                if (!cardAndAccountResults.existingCards().isEmpty() || !cardAndAccountResults.existingAccounts().isEmpty()) {
                    virtualExec.submit(new UpdateDbInfo(
                            cardAndAccountResults.existingCards(),
                            cardAndAccountResults.existingAccounts()
                    ));
                    foundChange = true;
                }
            }
            if (packPurchasesResults != null && !packPurchasesResults.isEmpty()) {
                virtualExec.submit(new PackMint(cardList, packPurchasesResults, mintService, nftAPI, logger));
                foundChange = true;
            }
            logger.log(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                    " | Additions: " + additions.size() +
                    " | Block scan took: " + (System.currentTimeMillis() - start) + " ms");

            if (foundChange) {
                logger.log(this.getClass(), TLogLevel.INFO, "Changes detected" +
                        " | Height: " + nextHeight +
                        " | Card NFTs: " + (cardAndAccountResults != null ? cardAndAccountResults.existingCards().toString() : "[]") +
                        " | Account NFTs: " + (cardAndAccountResults != null ? cardAndAccountResults.existingAccounts().toString() : "[]") +
                        " | Pack Purchases " + (packPurchasesResults != null ? packPurchasesResults : "[]"));
            }

            nextHeight++;

        } catch (
                Exception e) {
            logger.log(this.getClass(), TLogLevel.FAILED, "Failed to scan height: " + nextHeight +
                    " | Reason: " + e.getMessage(), e);
        } finally {
            semaphore.release();
        }
    }

    public int getHeight() {
        return nextHeight;
    }

    private class UpdateDbInfo implements Runnable {
        private final List<String> cardUpdates;
        private final List<AccountDid> accountUpdates;

        public UpdateDbInfo(List<String> cardUpdates, List<AccountDid> accountUpdates) {
            this.cardUpdates = cardUpdates;
            this.accountUpdates = accountUpdates;
        }

        @Override
        public void run() {
            try {
                for (var launcherId : cardUpdates) {
                    NftInfo newInfo = walletAPI.nftGetInfo(launcherId).data().orElseThrow(chiaExcept);
                    NftUpdate updatedInfo = new NftUpdate(
                            newInfo.nftCoinId(),
                            launcherId,
                            newInfo.ownerDid(),
                            nextHeight
                    );
                    nftAPI.updateCardDid(updatedInfo);
                }

                for (var didInfo : accountUpdates) {
                    NftInfo newInfo = walletAPI.nftGetInfo(didInfo.launcherId()).data().orElseThrow(chiaExcept);
                    NftUpdate updatedInfo = new NftUpdate(
                            newInfo.nftCoinId(),
                            didInfo.launcherId(),
                            newInfo.ownerDid(),
                            nextHeight,
                            newInfo.ownerDid().equals(didInfo.did())
                    );
                    nftAPI.updateAccountDid(updatedInfo);
                }
                logger.log(this.getClass(), TLogLevel.INFO, "Successfully updated NFTs: "
                        + List.of(cardUpdates, accountUpdates));
            } catch (Exception e) {
                logger.log(this.getClass(), TLogLevel.FAILED, "Failed updating NFTs: " +
                        List.of(cardUpdates, accountUpdates) +
                        " | Reason: " + e.getMessage(), e);
            }
        }
    }
}
