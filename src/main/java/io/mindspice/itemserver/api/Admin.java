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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;


@CrossOrigin
@RestController
@RequestMapping("/admin")
public class Admin {
    private final CardMintService mintService;
    private final TokenService tokenService;
    private final BlockchainMonitor blockchainMonitor;
    private final ScheduledExecutorService executorService;
    private final FullNodeAPI monNode;
    private final WalletAPI monWallet;
    private final FullNodeAPI transNode;
    private final WalletAPI transWallet;

    public Admin(
            @Qualifier("mintService") CardMintService mintService,
            @Qualifier("tokenService") TokenService tokenService,
            @Qualifier("blockchainMonitor") BlockchainMonitor blockchainMonitor,
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("monNodeApi") FullNodeAPI monNode,
            @Qualifier("monWalletApi") WalletAPI monWallet,
            @Qualifier("transactionNodeApi") FullNodeAPI transNode,
            @Qualifier("transactionWalletApi") WalletAPI transWallet
    ) {
        this.mintService = mintService;
        this.tokenService = tokenService;
        this.blockchainMonitor = blockchainMonitor;
        this.executorService = executor;
        this.monNode = monNode;
        this.monWallet = monWallet;
        this.transNode = transNode;
        this.transWallet = transWallet;
    }

    @PostMapping("/status")
    public ResponseEntity<String> status(@RequestBody String jsonReq) throws JsonProcessingException {
        try {
            var monNodeInfo = monNode.getBlockChainState().data().orElseThrow();
            var monWalletSync = monWallet.getSyncStatus().data().orElseThrow().synced();
            var monWalletHeight = monWallet.getHeightInfo().data().orElseThrow();
            var transNodeInfo = transNode.getBlockChainState().data().orElseThrow();
            var transWalletSync = transWallet.getSyncStatus().data().orElseThrow().synced();
            var transWalletHeight = transWallet.getHeightInfo().data().orElseThrow();

            ObjectNode status = new JsonUtils.ObjectBuilder()
                    .put("executor_task_size", ((ThreadPoolExecutor) executorService).getActiveCount())
                    .put("executor_max_size", ((ThreadPoolExecutor) executorService).getPoolSize())
                    .put("executor_core_size", ((ThreadPoolExecutor) executorService).getCorePoolSize())
                    .put("mint_queue_size", mintService.size())
                    .put("failed_mint_size", mintService.failedMintCount())
                    .put("token_queue_size", tokenService.size())
                    .put("failed_token_size", tokenService.failedTransactionCount())
                    .put("blockchain_monitor_height", blockchainMonitor.getHeight())
                    .put("mempool_current_cost", transNodeInfo.mempoolCost())
                    .put("mempool_pct_full", transNodeInfo.mempoolMaxTotalCost() / transNodeInfo.mempoolCost())
                    .put("mempool_fees", transNodeInfo.mempoolFees())
                    .put("mempool_min_fees", transNodeInfo.mempoolMinFees())
                    .put("monitor_node_synced", monNodeInfo.sync().synced())
                    .put("monitor_node_height", monNodeInfo.peak().height())
                    .put("mon_wallet_synced", monWalletSync)
                    .put("monitor_wallet_height", monWalletHeight)
                    .put("transaction_node_synced", transNodeInfo.sync().synced())
                    .put("transaction_node_height", transNodeInfo.peak().height())
                    .put("transaction_wallet_synced", transWalletSync)
                    .put("transaction_wallet_height", transWalletHeight)
                    .buildNode();
            JsonNode msg = JsonUtils.successMsg(status);
            return new ResponseEntity<>(JsonUtils.writeString(msg), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage())), HttpStatus.OK);
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
            tokenService.reSubmitFailedTransactions();
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.failMsg()), HttpStatus.OK);
        }
    }
}