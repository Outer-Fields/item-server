package io.mindspice.itemserver.monitor;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.AccountDid;
import io.mindspice.databaseservice.client.schema.NftUpdate;
import io.mindspice.itemserver.Settings;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.custom.CatSenderInfo;
import io.mindspice.jxch.rpc.schemas.object.CoinRecord;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.RPCException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;


public class BlockchainMonitor implements Runnable {
    private volatile int nextHeight;
    private final FullNodeAPI nodeAPI;
    private final WalletAPI walletAPI;
    private final OkraChiaAPI chiaAPI;
    private final OkraNFTAPI nftAPI;
    private final static Logger MONLOG = LogManager.getLogger("Mon-Log");
    private final static Logger SYSLOG = LogManager.getLogger("SYS-Log");
    // Add autowired reference here to use in this class, but have the rest manual managed

    protected final Supplier<RPCException> chiaExcept =
            () -> new RPCException("Required Chia RPC call returned Optional.empty");

    protected final Supplier<RPCException> serviceExcept =
            () -> new RPCException("Required Service RPC call returned Optional.empty");

    private final ExecutorService exec;

    public BlockchainMonitor(FullNodeAPI nodeAPI, WalletAPI walletAPI, OkraChiaAPI chiaAPI, OkraNFTAPI nftAPI,
            int startHeight, ExecutorService executorService) {

        nextHeight = startHeight;
        this.nodeAPI = nodeAPI;
        this.walletAPI = walletAPI;
        this.chiaAPI = chiaAPI;
        this.nftAPI = nftAPI;
        this.exec = executorService;

    }

    @Override
    public void run() {
        if (Settings.get().isPaused) return;
        try {
            if (!((nodeAPI.getHeight().data().orElseThrow() - Settings.get().heightBuffer) >= nextHeight)) {
                return;
            }
            long start = System.currentTimeMillis();
            List<CoinRecord> additions = chiaAPI.getCoinRecordsByHeight(nextHeight)
                    .data().orElseThrow(chiaExcept)
                    .removals();

            if (additions.isEmpty()) {
                nextHeight++; // Non-atomic inc doesn't matter, non-critical, is volatile to observe when monitoring
                return;
            }

            List<String> cardUpdateLauncher= new ArrayList<>();
            List<AccountDid> accountUpdateLaunchers = new ArrayList<>();

            for (var record : additions) {

                var cardCheck = nftAPI.checkIfCardExists(record.coin().parentCoinInfo());
                if (cardCheck.success()) {
                    cardUpdateLauncher.add(cardCheck.data().get()); // Safe to get since cardCheck returned success
                    continue; // Can continue since we have know and acknowledged what the coin is
                }

                var accountCheck = nftAPI.checkIfAccountNftExists(record.coin().parentCoinInfo());
                if (accountCheck.success()) {
                    accountUpdateLaunchers.add(accountCheck.data().get());
                    continue; // Can continue since we have know and acknowledged what the coin is
                }

                if (Settings.get().assetLookupTable.containsKey(record.coin().puzzleHash())) {
                    int amount = (int)(record.coin().amount() / 1000);
                    CatSenderInfo catInfo = nodeAPI.getCatSenderInfo(record).data().orElseThrow(chiaExcept);
                     if (!catInfo.assetId().equals(Settings.get().assetLookupTable.get(record.coin().puzzleHash()))) {
                         //TODO log wrong asset being set
                         continue; // Can continue and break out since wrong asset was sent
                     }


                    // TODO use a map to map address to card pack
                    // TODO add to mint list to submit after loop
                }

            }
            exec.submit(new UpdateDbInfo(cardUpdateLauncher,accountUpdateLaunchers));

            MONLOG.info("Last Updated Height: " + nextHeight +
                                "\tBlock Scan Took: " + ((System.currentTimeMillis() - start)) / 1000 + " Seconds");
            nextHeight++;
        } catch (Exception e) {
            MONLOG.fatal("!EXCEPTION! Failed To Monitor Height: " + nextHeight + e.getMessage());
            SYSLOG.error("!EXCEPTION! At Height: " + nextHeight + e.getMessage() + Arrays.toString(e.getStackTrace()));
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
                for(var launcherId : cardUpdates) {
                    NftInfo newInfo = walletAPI.nftGetInfo(launcherId).data().orElseThrow(chiaExcept);
                    NftUpdate updatedInfo = new NftUpdate(
                            newInfo.nftCoinId(),
                            launcherId,
                            newInfo.ownerDid(),
                            nextHeight
                    );
                    nftAPI.updateCardDid(updatedInfo);
                }

                for(var didInfo : accountUpdates) {
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

            } catch (RPCException | IllegalStateException e) {
                //TODO log this
            }
        }
    }
}
