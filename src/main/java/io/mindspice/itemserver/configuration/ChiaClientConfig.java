package io.mindspice.itemserver.configuration;

import io.mindspice.jxch.rpc.NodeConfig;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.RPCClient;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.transact.logging.TLogger;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;


@Configuration
public class ChiaClientConfig {
    // Monitor Client
    private NodeConfig monNodeConfig;
    private RPCClient monRpcClient;
    // Transaction Client
    private NodeConfig transactNodeConfig;
    private RPCClient transactRpcClient;

    @PostConstruct
    public void init() throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        // Monitor
        monNodeConfig = NodeConfig.loadConfig("mon_chia_node.yaml");
        monRpcClient = new RPCClient(monNodeConfig);
        // Transaction
        transactNodeConfig = NodeConfig.loadConfig("transact_chia_node.yaml");
        transactRpcClient = new RPCClient(transactNodeConfig);
    }

    @Bean
    public FullNodeAPI monNodeApi() {
        return new FullNodeAPI(monRpcClient);
    }

    @Bean
    public WalletAPI monWalletApi() {
        return new WalletAPI(monRpcClient);
    }

    @Bean
    public FullNodeAPI transactionNodeApi() {
        return new FullNodeAPI(monRpcClient);
    }

    @Bean
    public WalletAPI transactionWalletApi() {
        return new WalletAPI(monRpcClient);
    }
}
