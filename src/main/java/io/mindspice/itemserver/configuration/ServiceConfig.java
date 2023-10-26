package io.mindspice.itemserver.configuration;

import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.itemserver.schema.PackType;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.monitor.BlockchainMonitor;
import io.mindspice.itemserver.services.*;
import io.mindspice.jxch.rpc.http.FullNodeAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.MetaData;
import io.mindspice.jxch.rpc.util.ChiaUtils;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.http.UnsafeHttpJsonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Configuration
public class ServiceConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService executor() {
        return Executors.newScheduledThreadPool(6);
    }

    @Bean
    public UnsafeHttpJsonClient authResponseClient() {
        return new UnsafeHttpJsonClient();
    }

    @Bean
    public BlockchainMonitor blockchainMonitor(
            @Qualifier("mainNodeAPI") FullNodeAPI monNodeApi,
            @Qualifier("monWalletAPI") WalletAPI monWalletApi,
            @Qualifier("okraChiaAPI") OkraChiaAPI okraChiaAPI,
            @Qualifier("okraNFTAPI") OkraNFTAPI okraNFTAPI,
            @Qualifier("mintService") CardMintService mintService,
            @Qualifier("cardList") List<Card> cardList,
            @Qualifier("assetTable") Map<String, Pair<String, PackType>> assetTable,
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("customLogger") CustomLogger logger
    ) {
        BlockchainMonitor monitor = new BlockchainMonitor(
                monNodeApi, monWalletApi, okraChiaAPI, okraNFTAPI, mintService,
                assetTable, cardList, Settings.get().startHeight, logger);

        executor.scheduleAtFixedRate(monitor, 0, Settings.get().chainScanInterval, TimeUnit.MILLISECONDS);
        return monitor;
    }

    @Bean
    CardMintService mintService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("mainNodeAPI") FullNodeAPI nodeApi,
            @Qualifier("mintWalletAPI") WalletAPI walletApi,
            @Qualifier("customLogger") CustomLogger logger,
            @Qualifier("okraNFTAPI") OkraNFTAPI nftApi
    ) {
        try {
            JobConfig jobConfig = JobConfig.loadConfig(Settings.get().mintJobConfig);
            CardMintService cardMintService = new CardMintService(executor, jobConfig, logger, nodeApi, walletApi, nftApi);
            cardMintService.start();
            return cardMintService;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    TokenService okraTokenService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("mainNodeAPI") FullNodeAPI nodeApi,
            @Qualifier("transactWalletAPI") WalletAPI walletApi,
            @Qualifier("customLogger") CustomLogger logger,
            @Qualifier("okraNFTAPI") OkraNFTAPI nftApi
    ) {
        try {
            JobConfig jobConfig = JobConfig.loadConfig(Settings.get().okraJobConfig);
            TokenService tokenService = new TokenService(executor, jobConfig, logger, nodeApi, walletApi, nftApi);
            tokenService.start();
            return tokenService;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    TokenService outrTokenService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("mainNodeAPI") FullNodeAPI nodeApi,
            @Qualifier("transactWalletAPI") WalletAPI walletApi,
            @Qualifier("customLogger") CustomLogger logger,
            @Qualifier("okraNFTAPI") OkraNFTAPI nftApi
    ) {
        try {
            JobConfig jobConfig = JobConfig.loadConfig(Settings.get().outrJobConfig);
            TokenService tokenService = new TokenService(executor, jobConfig, logger, nodeApi, walletApi, nftApi);
            tokenService.start();
            return tokenService;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    RewardService rewardService(
            @Qualifier("okraGameAPI") OkraGameAPI okraGameAPI,
            @Qualifier("okraNFTAPI") OkraNFTAPI okraNFTAPI,
            @Qualifier("okraChiaAPI") OkraChiaAPI okraChiaAPI,
            @Qualifier("mintService") CardMintService mintService,
            @Qualifier("mainNodeAPI") FullNodeAPI nodeAPI,
            @Qualifier("okraTokenService") TokenService okraTokenService,
            @Qualifier("outrTokenService") TokenService outrTokenService,
            @Qualifier("cardList") List<Card> cardList,
            @Qualifier("customLogger") CustomLogger customLogger,
            @Qualifier("executor") ScheduledExecutorService exec) {
        RewardService rewardService = new RewardService(
                okraGameAPI,
                okraNFTAPI,
                okraChiaAPI,
                nodeAPI,
                mintService,
                okraTokenService,
                outrTokenService,
                cardList,
                customLogger
        );
        var timeToMidnight = ChronoUnit.MINUTES.between(LocalTime.now(), LocalTime.MIDNIGHT);
        exec.scheduleWithFixedDelay(rewardService, timeToMidnight, 1440, TimeUnit.MINUTES);
        return rewardService;
    }

    @Bean
    public S3Service s3Service() {
        return new S3Service();
    }

    @Bean
    AvatarService avatarService(
            @Qualifier("monWalletAPI") WalletAPI monWalletApi,
            @Qualifier("okraGameAPI") OkraGameAPI okraGameAPI,
            @Qualifier("s3Service") S3Service s3Service,
            @Qualifier("customLogger") CustomLogger customLogger
    ) {
        return new AvatarService(monWalletApi, okraGameAPI, s3Service, customLogger);
    }

    // <CatAddress,<AssetId,PackType>
    @Bean
    public Map<String, Pair<String, PackType>> assetTable() {
        HashMap<String, Pair<String, PackType>> packMap = new HashMap<>();
        packMap.put(
                Settings.get().boosterAddress,
                new Pair<>(Settings.get().boosterTail, PackType.BOOSTER)
        );
        packMap.put(
                Settings.get().starterAddress, // Address
                new Pair<>(Settings.get().starterTail, PackType.STARTER)
        );
        return packMap;
    }

    @Bean
    public List<Card> cardList(
            OkraNFTAPI nftApi
    ) {
        return nftApi.getCardCollection("test_cards").data()
                .orElseThrow(() -> new IllegalStateException("Failed to load card collection from database"));
    }

    @Bean
    MetaData accountNFTMeta() {
        return new MetaData(
                Collections.unmodifiableList(Settings.get().didUris),
                Collections.unmodifiableList(Settings.get().didMetaUris),
                Collections.unmodifiableList(Settings.get().didLicenseUris),
                Settings.get().didHash,
                Settings.get().didMetaHash,
                Settings.get().didLicenseHash,
                1,
                1
        );
    }

    @Bean
    public CustomLogger customLogger() {
        return new CustomLogger();
    }

}