package io.mindspice.itemserver.configuration;

import io.mindspice.itemserver.Settings;
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

    @Bean
    public RPCClient monRpcClient() throws IOException {
        NodeConfig monNodeConfig = NodeConfig.loadConfig(Settings.get().monNodeConfig);
        return new RPCClient(monNodeConfig);
    }

    @Bean
    public RPCClient transactRpcClient() throws IOException {
        NodeConfig transactNodeConfig = NodeConfig.loadConfig(Settings.get().transactNodeConfig);
        return new RPCClient(transactNodeConfig);
    }

    @Bean
    public FullNodeAPI monNodeApi(RPCClient monRpcClient) {
        return new FullNodeAPI(monRpcClient);
    }

    @Bean
    public WalletAPI monWalletApi(RPCClient monRpcClient) {
        return new WalletAPI(monRpcClient);
    }

    @Bean
    public FullNodeAPI transactionNodeApi(RPCClient transactRpcClient) {
        return new FullNodeAPI(transactRpcClient);
    }

    @Bean
    public WalletAPI transactionWalletApi(RPCClient transactRpcClient) {
        return new WalletAPI(transactRpcClient);
    }
}
