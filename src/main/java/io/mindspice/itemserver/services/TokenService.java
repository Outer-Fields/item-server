package io.mindspice.itemserver.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.Addition;
import io.mindspice.jxch.rpc.util.ChiaUtils;
import io.mindspice.jxch.transact.jobs.transaction.TransactionItem;
import io.mindspice.jxch.transact.jobs.transaction.TransactionService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.util.JsonUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;


public class TokenService extends TransactionService {
    private final List<TransactionItem> failedTransactions = new CopyOnWriteArrayList<>();

    public TokenService(ScheduledExecutorService executor, JobConfig config,
            TLogger tLogger, FullNodeAPI nodeAPI, WalletAPI walletAPI, boolean isCat) {
        super(executor, config, tLogger, nodeAPI, walletAPI, isCat);
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
    protected void onFinish(List<TransactionItem> list) {
        System.out.println("Finished: " + list);
    }

    public void reSubmitFailedTransactions() {
        submit(failedTransactions);
        failedTransactions.clear();
    }

    public int failedTransactionCount() {
        return failedTransactions.size();
    }

}
