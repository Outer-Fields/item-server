package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.RPCException;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.data.tuples.Pair;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;


public class CardMintService extends MintService {
    private final OkraNFTAPI nftApi;
    private final Supplier<RPCException> chiaExcept =
            () -> new RPCException("Required Chia RPC call returned Optional.empty");

    private final List<MintItem> failedMints = new CopyOnWriteArrayList<>();

    public CardMintService(ScheduledExecutorService scheduledExecutor, JobConfig config,
            TLogger tLogger, FullNodeAPI nodeAPI, WalletAPI walletAPI, OkraNFTAPI nftApi) {
        super(scheduledExecutor, config, tLogger, nodeAPI, walletAPI);
        this.nftApi = nftApi;
        tLogger.log(this.getClass(), TLogLevel.INFO, "Started Mint Service");

    }

    @Override
    protected void onFail(List<MintItem> list) {
        tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed Mint for UUIDs: " +
                list.stream().map(MintItem::uuid).toList());
            failedMints.addAll(list);
    }

    public void reSubmitFailedMints() {
            submit(failedMints);
            failedMints.clear();

    }

    public int failedMintCount() { {
            return failedMints.size();
        }
    }

    @Override
    protected void onFinish(Pair<List<MintItem>, List<String>> pair) {
        List<MintItem> mintItems = pair.first();
        List<String> nftLaunchers = pair.second();
        int height = -1;
        try {
            height = nodeAPI.getHeight().data().orElseThrow(chiaExcept);
        } catch (RPCException e) {
            tLogger.log(this.getClass(), TLogLevel.ERROR,
                        "Failed to fetch height for new NFT additions, attempting to continue, height will be set to -1...."
            );
        }

        // Both list will be the same size as guaranteed by the framework implementation
        for (int i = 0; i < mintItems.size(); ++i) {
            boolean isAccount = false;
            try {
                NftInfo nftInfo = walletAPI.nftGetInfo(nftLaunchers.get(i)).data().orElseThrow(chiaExcept);

                if (mintItems.get(i).uuid().contains("account:")) {
                    isAccount = true;
                    int playerId = Integer.parseInt(mintItems.get(i).uuid().split(":")[1]);
                    nftApi.addNewAccountNFT(
                            playerId, nftInfo.ownerDid(), nftInfo.nftCoinId(), nftLaunchers.get(i), height
                    );
                } else {
                    nftApi.addNewCardNFT(
                            nftInfo.ownerDid(), nftInfo.nftCoinId(), nftLaunchers.get(i),
                            nftInfo.licenseUris().get(1), height
                    );
                }
            } catch (Exception e) {
                tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed database addition for launcher ids: " +
                        nftLaunchers.get(i) + " | isAccount: " + isAccount);
            }
        }
    }
}
