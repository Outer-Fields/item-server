package io.mindspice.itemserver.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.itemserver.monitor.BlockchainMonitor;
import io.mindspice.itemserver.services.CardMintService;
import io.mindspice.itemserver.services.TokenService;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.mindlib.util.JsonUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;


@CrossOrigin
@RestController
@RequestMapping("/admin")
public class Admin {
    private final CardMintService mintService;
    private final TokenService okraTokenService;
    private final TokenService outrTokenService;
    private final BlockchainMonitor blockchainMonitor;
    private final ScheduledExecutorService executorService;
    private final FullNodeAPI monNode;
    private final WalletAPI monWallet;
    private final WalletAPI mintWallet;
    private final WalletAPI transWallet;

    public Admin(
            @Qualifier("mintService") CardMintService mintService,
            @Qualifier("okraTokenService") TokenService okraTokenService,
            @Qualifier("outrTokenService") TokenService outrTokenService,
            @Qualifier("blockchainMonitor") BlockchainMonitor blockchainMonitor,
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("mainNodeAPI") FullNodeAPI monNode,
            @Qualifier("monWalletAPI") WalletAPI monWallet,
            @Qualifier("mintWalletAPI") WalletAPI mintWallet,
            @Qualifier("transactWalletAPI") WalletAPI transWallet
    ) {
        this.mintService = mintService;
        this.okraTokenService = okraTokenService;
        this.outrTokenService = outrTokenService;
        this.blockchainMonitor = blockchainMonitor;
        this.executorService = executor;
        this.monNode = monNode;
        this.monWallet = monWallet;
        this.mintWallet = mintWallet;
        this.transWallet = transWallet;
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() throws JsonProcessingException {
        try {
            var monNodeInfo = monNode.getBlockChainState().data().orElseThrow();
//            var transNodeSync = transWallet.getSyncStatus().data().orElseThrow().synced();
//            var transNodeHeight = transWallet.getHeightInfo().data().orElseThrow();
//            var mintNodeSync = mintWallet.getSyncStatus().data().orElseThrow().synced();
//            var mintNodeHeight = mintWallet.getHeightInfo().data().orElseThrow();
//            var monWalletSync = mintWallet.getSyncStatus().data().orElseThrow().synced();
//            var monWalletHeight = mintWallet.getHeightInfo().data().orElseThrow();

            ObjectNode status = new JsonUtils.ObjectBuilder()
                    .put("executor_task_size", ((ThreadPoolExecutor) executorService).getActiveCount())
                    .put("executor_max_size", ((ThreadPoolExecutor) executorService).getPoolSize())
                    .put("executor_core_size", ((ThreadPoolExecutor) executorService).getCorePoolSize())
                    .put("mint_queue_size", mintService.size())
                    .put("mint_failed_size", mintService.failedMintCount())
                    .put("okra_queue_size", okraTokenService.size())
                    .put("okra_failed_size", okraTokenService.failedTransactionCount())
                    .put("outr_queue_size", outrTokenService.size())
                    .put("outr_fail_size", outrTokenService.failedTransactionCount())
                    .put("failed_token_size", okraTokenService.failedTransactionCount())
                    .put("blockchain_monitor_height", blockchainMonitor.getHeight())
                    .put("mempool_current_cost", monNodeInfo.mempoolCost())
                    .put("mempool_pct_full", monNodeInfo.mempoolCost() / monNodeInfo.mempoolMaxTotalCost())
                    .put("mempool_fees", monNodeInfo.mempoolFees())
                    .put("mempool_min_fees", monNodeInfo.mempoolMinFees())
                    .put("monitor_node_synced", monNodeInfo.sync().synced())
                    .put("monitor_node_height", monNodeInfo.peak().height())
//                    .put("monitor_wallet_synced", monWalletSync)
//                    .put("monitor_wallet_height", monWalletHeight)
//                    .put("transaction_wallet_synced", transNodeSync)
//                    .put("transaction_wallet_height", transNodeHeight)
//                    .put("mint_wallet_synced", mintNodeSync)
//                    .put("mint_wallet_height", mintNodeHeight)
                    .buildNode();
            JsonNode msg = JsonUtils.successMsg(status);
            return new ResponseEntity<>(JsonUtils.writeString(msg), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage() + Arrays.toString(e.getStackTrace()))), HttpStatus.OK);
        }
    }

    @PostMapping("/resubmit_failed_mints")
    public ResponseEntity<String> reSubmitFailedMints(@RequestBody String jsonReq) throws JsonProcessingException {
        JsonNode node = JsonUtils.readTree(jsonReq);
        if (node.get("resubmit").asBoolean()) {
            mintService.reSubmitFailedMints();
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.failMsg()), HttpStatus.OK);
        }
    }

    @PostMapping("/resubmit_failed_transactions")
    public ResponseEntity<String> reSubmitFailedSends(@RequestBody String jsonReq) throws JsonProcessingException {
        JsonNode node = JsonUtils.readTree(jsonReq);
        if (node.get("resubmit").asBoolean()) {
            okraTokenService.reSubmitFailedTransactions();
            outrTokenService.reSubmitFailedTransactions();
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.failMsg()), HttpStatus.OK);
        }
    }


}