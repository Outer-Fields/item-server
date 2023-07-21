package io.mindspice.itemserver.configuration;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.itemserver.Schema.PackType;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.monitor.BlockchainMonitor;
import io.mindspice.itemserver.services.CardMintService;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.data.Pair;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cglib.core.Block;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


@Configuration
public class ServiceConfig {
    // Transact Logger
    private TLogger tLogger;

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService executor() {
        return Executors.newScheduledThreadPool(6);
    }

    @Bean
    public BlockchainMonitor blockchainMonitor(
            @Qualifier("monNodeApi") FullNodeAPI monNodeApi,
            @Qualifier("monWalletApi") WalletAPI monWalletApi,
            @Qualifier("okraChiaApi") OkraChiaAPI okraChiaAPI,
            @Qualifier("okraNFTApi") OkraNFTAPI okraNFTAPI,
            @Qualifier("executor") ScheduledExecutorService executor
    ) {
        return new BlockchainMonitor(monNodeApi, monWalletApi, okraChiaAPI,
                                     okraNFTAPI, Settings.get().startHeight, executor);
    }

    @Bean
    MintService mintService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("transactionNodeApi") FullNodeAPI nodeApi,
            @Qualifier("transactionWalletApi") WalletAPI walletApi
    ) {
        JobConfig jobConfig = null;
        return new CardMintService(executor, jobConfig, tLogger, nodeApi, walletApi);
    }

    public static HashMap<String, PackType> assetPackMap () {
        HashMap<String, PackType> packMap = new HashMap<>();
        packMap.put("puzzle_hash", PackType.STARTER);
        packMap.put("puzzle_hash", PackType.BOOSTER);
        return packMap;
    }
}