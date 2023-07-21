package io.mindspice.itemserver.services;

import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.data.Pair;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;


public class CardMintService extends MintService {
    public CardMintService(ScheduledExecutorService scheduledExecutor, JobConfig config,
            TLogger tLogger, FullNodeAPI nodeAPI, WalletAPI walletAPI) {
        super(scheduledExecutor, config, tLogger, nodeAPI, walletAPI);
    }



    @Override
    protected void onFail(List<MintItem> list) {

    }

    @Override
    protected void onFinish(Pair<List<MintItem>, List<String>> pair) {

    }
}
