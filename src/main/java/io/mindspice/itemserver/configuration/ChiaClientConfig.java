package io.mindspice.itemserver.configuration;

import io.mindspice.itemserver.Settings;
import io.mindspice.jxch.rpc.NodeConfig;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.RPCClient;
import io.mindspice.jxch.rpc.http.WalletAPI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;


@Configuration
public class ChiaClientConfig {

    @Bean
    public RPCClient mainNodeRpcClient() throws IOException {
        NodeConfig monNodeConfig = NodeConfig.loadConfig(Settings.get().mainNodeConfig);
        return new RPCClient(monNodeConfig);
    }

    @Bean
    public RPCClient mintRpcClient() throws IOException {
        NodeConfig transactNodeConfig = NodeConfig.loadConfig(Settings.get().mintWalletConfig);
        return new RPCClient(transactNodeConfig);
    }

    @Bean
    public RPCClient transactRpcClient() throws IOException {
        NodeConfig transactNodeConfig = NodeConfig.loadConfig(Settings.get().transactionWalletConfig);
        return new RPCClient(transactNodeConfig);
    }

    @Bean
    public FullNodeAPI mainNodeAPI(@Qualifier("mainNodeRpcClient") RPCClient mainNodeRpcClient) {
        return new FullNodeAPI(mainNodeRpcClient);
    }

    @Bean
    WalletAPI monWalletAPI(@Qualifier("mainNodeRpcClient") RPCClient mainNodeRpcClient) {
        return new WalletAPI(mainNodeRpcClient);
    }

    @Bean
    public WalletAPI mintWalletAPI(@Qualifier("mintRpcClient") RPCClient mintRpcClient) {
        return new WalletAPI(mintRpcClient);
    }

    @Bean
    public WalletAPI transactWalletAPI(@Qualifier("transactRpcClient") RPCClient transactRpcClient) {
        return new WalletAPI(transactRpcClient);
    }

}
