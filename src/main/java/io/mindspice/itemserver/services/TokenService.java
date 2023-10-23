package io.mindspice.itemserver.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.TransactionLog;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;

import io.mindspice.jxch.rpc.schemas.object.Coin;
import io.mindspice.jxch.rpc.util.ChiaUtils;
import io.mindspice.jxch.transact.service.transaction.TransactionItem;
import io.mindspice.jxch.transact.service.transaction.TransactionService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.util.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;


public class TokenService extends TransactionService {
    private final List<TransactionItem> failedTransactions = new CopyOnWriteArrayList<>();
    private final OkraNFTAPI nftAPI;

    public TokenService(ScheduledExecutorService executor, JobConfig config,
            TLogger tLogger, FullNodeAPI nodeAPI, WalletAPI walletAPI, OkraNFTAPI nftAPI) {
        super(executor, config, tLogger, nodeAPI, walletAPI);
        this.nftAPI = nftAPI;
        tLogger.log(this.getClass(), TLogLevel.INFO, "Started Token Service");

    }

    @Override
    protected void onFail(List<TransactionItem> list) {
        tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed Transaction for UUIDs: " +
                list.stream().map(TransactionItem::uuid).toList());
        try {
            tLogger.log(this.getClass(), TLogLevel.FAILED, "Failed transactions json: " + JsonUtils.writeString(list));
        } catch (JsonProcessingException e) {
            tLogger.log(this.getClass(), TLogLevel.ERROR,
                    "Failed Writing Failed Transactions, Reverting To Java Deserialization: " + list, e);
        }
        failedTransactions.addAll(list);
    }

    @Override
    protected void onFinish(List<TransactionItem> txList) {
        var txLogs = txList.stream()
                .map(t -> new TransactionLog(
                        t.uuid(),
                        t.coin().puzzleHash(),
                        t.coin().amount(),
                        ChiaUtils.getCoinId(t.coin())))
                .toList();

        txLogs.forEach(l -> Thread.ofVirtual().start(() -> {
            try {
                var resp = nftAPI.addTransactionLog(l);
                if (!resp.success()) {
                    tLogger.log(this.getClass(), TLogLevel.ERROR, " Failed to add transaction log:" + l);
                }
            } catch (Exception e) {
                tLogger.log(this.getClass(), TLogLevel.ERROR, " Failed to add transaction log:" + l);
            }
        }));
    }

    public void reSubmitFailedTransactions() {
        submit(failedTransactions);
        failedTransactions.clear();
    }

    public int failedTransactionCount() {
        return failedTransactions.size();
    }

}
