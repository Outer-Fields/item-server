package io.mindspice.itemserver.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.MintLog;
import io.mindspice.itemserver.util.CustomLogger;
import io.mindspice.itemserver.util.Utils;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.JsonUtils;
import io.mindspice.jxch.rpc.util.RPCException;
import io.mindspice.jxch.transact.service.mint.MintItem;
import io.mindspice.jxch.transact.service.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.util.FuncUtils;

import java.util.ArrayList;
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
            CustomLogger tLogger, FullNodeAPI nodeAPI, WalletAPI walletAPI, OkraNFTAPI nftApi) {
        super(scheduledExecutor, config, tLogger, nodeAPI, walletAPI);
        this.nftApi = nftApi;
        tLogger.logApp(this.getClass(), TLogLevel.INFO, "Started Mint Service");

    }

    @Override
    protected void onFail(List<MintItem> failList) {
        failList.forEach(i -> nftApi.addFailedMint(i.uuid(), "Returned Failed"));
        try {
            tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed Mint Json: " + JsonUtils.writeString(failList));
        } catch (JsonProcessingException e) {
            tLogger.log(this.getClass(), TLogLevel.ERROR,
                    "Failed Writing Failed Mints, Reverting To Java Deserialization: " + failList, e);
        }
        failedMints.addAll(failList);
    }

    public void reSubmitFailedMints() {
        submit(failedMints);
        failedMints.clear();

    }

    public int failedMintCount() {
        return failedMints.size();

    }

    @Override
    protected void onFinish(List<MintItem> mints) {

        tLogger.log(this.getClass(), TLogLevel.INFO, "Finished Mints: " +
                FuncUtils.defaultOnExcept(() -> JsonUtils.writeString(mints.stream().map(MintItem::nftId).toList()), "[exception]")
        );

        if (mints.isEmpty()) { return; }// should never happen;
        int height = -1;
        try {
            height = nodeAPI.getHeight().data().orElseThrow(chiaExcept);
        } catch (RPCException e) {
            tLogger.log(this.getClass(), TLogLevel.ERROR,
                    "Failed to fetch height for new NFT additions, attempting to continue, height will be set to -1...."
            );
        }

        // Both list will be the same size as guaranteed by the framework implementation
        List<MintLog> mintLogs = new ArrayList<>();
        mintLogs.add(new MintLog(mints.get(0).uuid(), mints.get(0).targetAddress()));
        for (var mint : mints) {
            if (mintLogs.getLast().getUUID().equals(mint.uuid())) {
                mintLogs.getLast().addNftId(mint.nftId());
            } else {
                mintLogs.add(new MintLog(mint.uuid(), mint.targetAddress()));
                mintLogs.getLast().addNftId(mint.nftId());
            }

            boolean isAccount = false;
            try {
                NftInfo nftInfo = Utils.nftGetInfoWrapper(walletAPI, mint.nftId());

                if (mint.uuid().contains("account:")) {
                    isAccount = true;
                    var resp = nftApi.addNewAccountNFT(
                            -1, nftInfo.ownerDid(), nftInfo.nftCoinId(), nftInfo.launcherId(), height
                    );
                    if (resp.error()) { throw new IllegalStateException(resp.error_msg()); }
                } else {
                    var resp = nftApi.addNewCardNFT(
                            nftInfo.ownerDid(), nftInfo.nftCoinId(), nftInfo.launcherId(),
                            Utils.uidFromUrl(nftInfo.dataUris().get(0)), height
                    );
                    if (resp.error()) { throw new IllegalStateException(resp.error_msg()); }
                }
            } catch (Exception e) {
                tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed database addition for nft ids: " +
                        mint.nftId() + " | isAccount: " + isAccount);
            }
        }
        mintLogs.forEach(l -> Thread.ofVirtual().start(() -> {
            try {
                var resp = nftApi.addMintLog(l);
                if (!resp.success()) {
                    tLogger.log(this.getClass(), TLogLevel.ERROR, " Failed to add mint log:" +
                            FuncUtils.defaultOnExcept(() -> JsonUtils.writeString(l), "[exception]"));
                }
            } catch (Exception e) {
                tLogger.log(this.getClass(), TLogLevel.ERROR, " Failed to add mint log:" +
                        FuncUtils.defaultOnExcept(() -> JsonUtils.writeString(l), "[exception]"));
            }
        }));

    }
}
