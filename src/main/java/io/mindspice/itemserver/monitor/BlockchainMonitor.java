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
import io.mindspice.itemserver.services.CustomLogger;
import io.mindspice.itemserver.services.PackMint;
import io.mindspice.itemserver.util.Utils;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.ApiResponse;
import io.mindspice.jxch.rpc.schemas.custom.CatSenderInfo;
import io.mindspice.jxch.rpc.schemas.fullnode.AdditionsAndRemovals;
import io.mindspice.jxch.rpc.schemas.object.CoinRecord;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.ChiaUtils;
import io.mindspice.jxch.rpc.util.JsonUtils;
import io.mindspice.jxch.rpc.util.RPCException;
import io.mindspice.jxch.transact.service.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.util.FuncUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class BlockchainMonitor implements Runnable {
    private volatile int nextHeight;
    private final FullNodeAPI nodeAPI;
    private final WalletAPI walletAPI;
    private final OkraChiaAPI chiaAPI;
    private final OkraNFTAPI nftAPI;
    private final MintService mintService;
    private final Map<String, Pair<String, PackType>> assetMap;
    private final List<Card> cardList;
    private final CustomLogger logger;
    private final Semaphore semaphore = new Semaphore(1);

    protected final Supplier<RPCException> chiaExcept =
            () -> new RPCException("Required Chia RPC call returned Optional.empty");

    private final ExecutorService virtualExec = Executors.newVirtualThreadPerTaskExecutor();

    public BlockchainMonitor(
            FullNodeAPI nodeAPI, WalletAPI walletAPI,
            OkraChiaAPI chiaAPI, OkraNFTAPI nftAPI,
            MintService mintService, Map<String,
            Pair<String, PackType>> assetMap,
            List<Card> cardList, int startHeight,
            CustomLogger logger) {

        nextHeight = startHeight;
        this.nodeAPI = nodeAPI;
        this.walletAPI = walletAPI;
        this.chiaAPI = chiaAPI;
        this.nftAPI = nftAPI;
        this.mintService = mintService;
        this.assetMap = assetMap;
        this.cardList = cardList;
        this.logger = logger;
        logger.logApp(this.getClass(), TLogLevel.INFO, "Started Blockchain Monitor");
    }

    public int getNextHeight() {
        return nextHeight;
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

            AdditionsAndRemovals coinRecords = chiaAPI.getCoinRecordsByHeight(nextHeight)
                    .data().orElseThrow(chiaExcept);

            List<CoinRecord> additions = coinRecords.additions().stream().filter(a -> !a.coinbase()).toList();
            List<CoinRecord> removals = coinRecords.removals().stream().filter(a -> !a.coinbase()).toList();

            if (additions.isEmpty()) {
                logger.logApp(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                        " | Additions: 0" +
                        " | Block scan took: " + (System.currentTimeMillis() - start) + " ms");
                nextHeight++; // Non-atomic inc doesn't matter, non-critical, is volatile to observe when monitoring
                return;
            }

            // NOTE offers need to be parsed via finding the parent in the removals since they do some intermediate operations
            CompletableFuture<CardAndAccountCheck> cardAndAccountCheck = CompletableFuture.supplyAsync(() -> {
                var cardOrAccountUpdates = nftAPI.checkIfCardOrAccountExists(
                        Stream.concat(additions.stream(), removals.stream())
                                .map(a -> a.coin().parentCoinInfo())
                                .distinct().toList()
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
                                logger.logApp(this.getClass(), TLogLevel.ERROR, " Failed asset lookup, wrong asset?" +
                                        " | Coin: " + ChiaUtils.getCoinId(record.coin()) +
                                        " | Amount(mojos / 1000): " + amount +
                                        " | Error: " + catInfoResp.error());
                                continue;
                            }

                            CatSenderInfo catInfo = catInfoResp.data().orElseThrow(chiaExcept);
                            if (!catInfo.assetId().equals(assetMap.get(record.coin().puzzleHash()).first())) {
                                logger.logApp(this.getClass(), TLogLevel.INFO, "Wrong Asset Received: " + catInfo.assetId()
                                        + " Expected: " + assetMap.get(record.coin().puzzleHash()).first()
                                        + " Amount: " + record.coin().amount());
                                continue;
                            }

                            PackType packType = assetMap.get(record.coin().puzzleHash()).second();
                            for (int i = 0; i < amount; ++i) {
                                String uuid = UUID.randomUUID().toString();
                                logger.logApp(this.getClass(), TLogLevel.INFO, "Submitted pack purchase " +
                                        " | UUID: " + uuid +
                                        " | Coin: " + ChiaUtils.getCoinId(record.coin()) +
                                        " | Amount(mojos / 1000): " + amount +
                                        " | Asset: " + catInfo.assetId() +
                                        " | Sender Address" + catInfo.senderPuzzleHash()
                                );
                                packPurchases.add(new PackPurchase(catInfo.senderPuzzleHash(), packType, uuid));
                            }
                        } catch (RPCException e) {
                            logger.logApp(this.getClass(), TLogLevel.FAILED, "Failed asset lookups at height: " + nextHeight +
                                    " | Reason: " + e.getMessage() +
                                    " | Coin: " + ChiaUtils.getCoinId(record.coin()) +
                                    " | Amount(mojos / 1000): " + amount);
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
            logger.logApp(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                    " | Additions: " + additions.size() +
                    " | Block scan took: " + (System.currentTimeMillis() - start) + " ms");

            if (foundChange) {
                logger.logApp(this.getClass(), TLogLevel.INFO, "Changes detected" +
                        " | Height: " + nextHeight +
                        " | Card NFTs: " + (cardAndAccountResults != null ? cardAndAccountResults.existingCards().toString() : "[]") +
                        " | Account NFTs: " + (cardAndAccountResults != null ? cardAndAccountResults.existingAccounts().toString() : "[]") +
                        " | Pack Purchases " + (packPurchasesResults != null ? packPurchasesResults : "[]"));
            }

            nextHeight++;

        } catch (
                Exception e) {
            logger.logApp(this.getClass(), TLogLevel.FAILED, "Failed to scan height: " + nextHeight +
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
                long startTime = System.currentTimeMillis();
                for (var launcherId : cardUpdates) {
                    NftInfo newInfo = Utils.nftGetInfoWrapper(walletAPI, launcherId);
                    NftUpdate updatedInfo = new NftUpdate(
                            newInfo.nftCoinId(),
                            launcherId,
                            newInfo.ownerDid(),
                            nextHeight
                    );
                    var updateRtn = nftAPI.updateCardDid(updatedInfo);
                    if (!updateRtn.success()) {
                        logger.logApp(this.getClass(), TLogLevel.ERROR, "Failed Updating card launcher: "
                                + updatedInfo.launcherId() + " with coin :" + newInfo.nftCoinId() + " @ Height : "
                                + (nextHeight - 1) + " Reason: " + updateRtn.error_msg());
                    }
                }

                for (var didInfo : accountUpdates) {
                    NftInfo newInfo = Utils.nftGetInfoWrapper(walletAPI, didInfo.launcherId());
                    NftUpdate updatedInfo = new NftUpdate(
                            newInfo.nftCoinId(),
                            didInfo.launcherId(),
                            newInfo.ownerDid(),
                            nextHeight,
                            newInfo.ownerDid() != null && newInfo.ownerDid().equals(didInfo.did())
                    );
                    var updateRtn = nftAPI.updateAccountDid(updatedInfo);
                    if (!updateRtn.success()) {
                        logger.logApp(this.getClass(), TLogLevel.ERROR, "Failed Updating account launcher: "
                                + didInfo.launcherId() + " with coin :" + newInfo.nftCoinId() + " @ Height : "
                                + (nextHeight - 1) + " Reason: " + updateRtn.error_msg());
                    }
                }
                logger.logApp(this.getClass(), TLogLevel.INFO, "Successfully updated NFTs: "
                        + "Cards:" + cardUpdates + " | accounts: " + accountUpdates
                        + " | Update took: " + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                logger.logApp(this.getClass(), TLogLevel.FAILED, "Failed updating NFTs: " +
                        cardUpdates + accountUpdates +
                        " | Reason: " + e.getMessage(), e);
            }
        }
    }
}
