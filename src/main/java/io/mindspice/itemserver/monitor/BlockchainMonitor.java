package io.mindspice.itemserver.monitor;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.AccountDid;
import io.mindspice.databaseservice.client.schema.Card;
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
import io.mindspice.jxch.rpc.schemas.wallet.Addition;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.RPCException;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.mindlib.data.tuples.Pair;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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
    // Add autowired reference here to use in this class, but have the rest manual managed

    protected final Supplier<RPCException> chiaExcept =
            () -> new RPCException("Required Chia RPC call returned Optional.empty");

    private final ExecutorService exec;
    private final ExecutorService virtualExec = Executors.newVirtualThreadPerTaskExecutor();

    public BlockchainMonitor(
            FullNodeAPI nodeAPI, WalletAPI walletAPI,
            OkraChiaAPI chiaAPI, OkraNFTAPI nftAPI,
            MintService mintService, Map<String, Pair<String, PackType>> assetMap,
            List<Card> cardList, int startHeight,
            ExecutorService executorService, TLogger logger) {

        nextHeight = startHeight;
        this.nodeAPI = nodeAPI;
        this.walletAPI = walletAPI;
        this.chiaAPI = chiaAPI;
        this.nftAPI = nftAPI;
        this.mintService = mintService;
        this.assetMap = assetMap;
        this.cardList = cardList;
        this.exec = executorService;
        this.logger = logger;
        logger.log(this.getClass(), TLogLevel.INFO, "Started Blockchain Monitor");
    }

    @Override
    public void run() {
        if (Settings.get().isPaused) { return; }
        try {
            if (!((nodeAPI.getHeight().data().orElseThrow() - Settings.get().heightBuffer) >= nextHeight)) {
                return;
            }
            long start = System.currentTimeMillis();

            List<CoinRecord> additions = chiaAPI.getCoinRecordsByHeight(nextHeight)
                    .data().orElseThrow(chiaExcept)
                    .additions().stream().filter(a -> !a.coinbase()).toList();

            if (additions.isEmpty()) {
                logger.log(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                        " | Block scan took: " + ((System.currentTimeMillis() - start)) / 1000 + " Seconds");
                nextHeight++; // Non-atomic inc doesn't matter, non-critical, is volatile to observe when monitoring
                return;
            }


            System.out.println("Additions size:" + additions.size());

            List<String> cardUpdateLaunchers = Collections.synchronizedList(new ArrayList<>());
            List<AccountDid> accountUpdateLaunchers = Collections.synchronizedList(new ArrayList<>());
            List<PackPurchase> packPurchases = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(additions.size() * 2);
            Semaphore semaphore = new Semaphore(120);

            for (var record : additions) {

                virtualExec.submit(() -> {
                    try {
                        semaphore.acquire();
                        var cardCheck = nftAPI.checkIfCardExists(record.coin().parentCoinInfo());
                        if (cardCheck.success()) {
                            cardUpdateLaunchers.add(cardCheck.data().get()); // Safe to get since cardCheck returned success
                        }
                    } catch (Exception e) {
                        logger.log(this.getClass(), TLogLevel.FAILED, "Exception on card check thread at height: " + nextHeight +
                                " | Reason: " + e.getMessage(), e);
                    } finally {
                        latch.countDown();
                        semaphore.release();
                    }
                });

                virtualExec.submit(() -> {
                    try {
                        semaphore.acquire();
                        var accountCheck = nftAPI.checkIfAccountNftExists(record.coin().parentCoinInfo());
                        if (accountCheck.success()) {
                            accountUpdateLaunchers.add(accountCheck.data().get());// Safe to get since accountCheck returned success
                        }
                    } catch (Exception e) {
                        logger.log(this.getClass(), TLogLevel.FAILED, "Exception on account check thread at height: " + nextHeight +
                                " | Reason: " + e.getMessage(), e);
                    } finally {
                        latch.countDown();
                        semaphore.release();
                    }
                });

                if (assetMap.containsKey(record.coin().puzzleHash())) {
                    int amount = (int) (record.coin().amount() / 1000);
                    ApiResponse<CatSenderInfo> catInfoResp = nodeAPI.getCatSenderInfo(record);
                    if (!catInfoResp.success()) {
                        logger.log(this.getClass(), TLogLevel.ERROR, " Failed asset lookup, wrong asset?" +
                                " | Error: " + catInfoResp.error());
                    }
                    CatSenderInfo catInfo = catInfoResp.data().orElseThrow(chiaExcept);
                    if (!catInfo.assetId().equals(assetMap.get(record.coin().puzzleHash()).first())) {
                        logger.log(this.getClass(), TLogLevel.INFO, "Wrong Asset Received: " + catInfo.assetId()
                                + " Expected: " + assetMap.get(record.coin().puzzleHash()).first()
                                + " Amount: " + record.coin().amount());
                        continue; // Can continue and break out since wrong asset was sent
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
                }

            }
            latch.await();

            boolean foundChange = false;
            if (!cardUpdateLaunchers.isEmpty() || !accountUpdateLaunchers.isEmpty()) {
                exec.submit(new UpdateDbInfo(cardUpdateLaunchers, accountUpdateLaunchers));
                foundChange = true;
            }
            if (!packPurchases.isEmpty()) {
                exec.submit(new PackMint(cardList, packPurchases, mintService));
                foundChange = true;
            }
            logger.log(this.getClass(), TLogLevel.INFO, "Finished scan of block height: " + nextHeight +
                    " | Block scan took: " + ((System.currentTimeMillis() - start)) / 1000 + " Seconds");


            if(foundChange) {
                logger.log(this.getClass(), TLogLevel.INFO, "Changes detected" +
                        " | Height: " + nextHeight +
                        " | Card NFTs: " + cardUpdateLaunchers +
                        " | Account NFTs: " + accountUpdateLaunchers +
                        " | Pack Purchases " + packPurchases);
            }
            nextHeight++;

        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.FAILED, "Failed to scan height: " + nextHeight +
                    " | Reason: " + e.getMessage(), e);
            // " | Stack Trace: " + Arrays.toString(e.getStackTrace()));
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
                } // TODO add height here
                logger.log(this.getClass(), TLogLevel.INFO, "Successfully updated NFTs: "
                        + List.of(cardUpdates, accountUpdates));
            } catch (Exception e) {
                logger.log(this.getClass(), TLogLevel.FAILED, "Failed updating NFTs: " +
                        List.of(cardUpdates, accountUpdates) +
                        " | Reason: " + e.getMessage() +
                        " | Stack Trace: " + Arrays.toString(e.getStackTrace()));
            }
        }
    }
}
