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
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.settings.JobConfig;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.http.UnsafeHttpJsonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
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
        return Executors.newScheduledThreadPool(10);
    }

    @Bean
    public UnsafeHttpJsonClient authResponseClient() {
        return new UnsafeHttpJsonClient();
    }

    @Bean
    public BlockchainMonitor blockchainMonitor(
            @Qualifier("monNodeApi") FullNodeAPI monNodeApi,
            @Qualifier("monWalletApi") WalletAPI monWalletApi,
            @Qualifier("okraChiaApi") OkraChiaAPI okraChiaAPI,
            @Qualifier("okraNFTApi") OkraNFTAPI okraNFTAPI,
            @Qualifier("mintService") MintService mintService,
            @Qualifier("cardList") List<Card> cardList,
            @Qualifier("assetTable") Map<String, Pair<String, PackType>> assetTable,
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("customLogger") TLogger logger
    ) {
        BlockchainMonitor monitor = new BlockchainMonitor(
                monNodeApi, monWalletApi, okraChiaAPI, okraNFTAPI, mintService,
                assetTable, cardList, Settings.get().startHeight, logger);

        executor.scheduleAtFixedRate(monitor, 0, 5, TimeUnit.SECONDS);
        return monitor;
    }

    @Bean
    CardMintService mintService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("monNodeApi") FullNodeAPI nodeApi,
            @Qualifier("monWalletApi") WalletAPI walletApi,
            @Qualifier("customLogger") TLogger logger,
            @Qualifier("okraNFTApi") OkraNFTAPI nftApi
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
    TokenService tokenService(
            @Qualifier("executor") ScheduledExecutorService executor,
            @Qualifier("monNodeApi") FullNodeAPI nodeApi,
            @Qualifier("monWalletApi") WalletAPI walletApi,
            @Qualifier("customLogger") TLogger logger,
            @Qualifier("okraNFTApi") OkraNFTAPI nftApi
    ) {
        try {
            JobConfig jobConfig = JobConfig.loadConfig(Settings.get().tokenJobConfig);
            TokenService tokenService = new TokenService(executor, jobConfig, logger, nodeApi, walletApi, false);
            tokenService.start();

//            var t1 = new TransactionItem(
//                    new Addition("f5698a098d6e89052ad7e829afbd545c21b137cd749ea11c633a8b20194f0a1d", ChiaUtils.catToMojos(0.2).longValue())
//            );
//            var t2 = new TransactionItem(
//                    new Addition("778fe3a85247cc0e22a75861d9045865ac8a2b995dc0b5606caf9eac8f0add9b", ChiaUtils.catToMojos(0.2).longValue())
//            );
//            var t3 = new TransactionItem(
//                    new Addition("ecefc3a3316727f4abfdd5d52d74888962b890618a0944d3b6ae709f09eedf6c", ChiaUtils.catToMojos(0.2).longValue())
//            );
//            tokenService.submit(List.of(t1,t2,t3));
//            System.out.println(tokenService.size());
//            System.out.println(tokenService.isRunning());

            return tokenService;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public S3Service s3Service() {
        return new S3Service();
    }

    @Bean
    AvatarService avatarService(
            @Qualifier("monWalletApi") WalletAPI monWalletApi,
            @Qualifier("okraGameApi") OkraGameAPI okraGameAPI,
            @Qualifier("s3Service") S3Service s3Service,
            @Qualifier("customLogger") CustomLogger customLogger
    ) {
        return new AvatarService(monWalletApi, okraGameAPI, s3Service(), customLogger);
    }


    @Bean
    //<CatAddress,<AssetId,PackType>
    public Map<String, Pair<String, PackType>> assetTable() {
        HashMap<String, Pair<String, PackType>> packMap = new HashMap<>();
        packMap.put(
                "0x269d670b4816d8b7e411567d3994ac565f32707e1733a7e4ceea9d549621541f",
                new Pair<>("0xd8a533fbac72f18894bafcce792190cf43bea96a127dca30088d2e9fd4a4e3e9", PackType.STARTER)
        );
        packMap.put(
                "0x922bbe4541cd140ccd9021d3a6125aad03075ec165edc15431bd2a6036e20b60", // Address
                new Pair<>("0x055fe650c6bbcd5820ba326f0cec9facf70637c5cbea83e6ef8c7e1fc037cb06", PackType.BOOSTER) // Asset Id, Type
        );
        return packMap;
    }

    @Bean
    public List<Card> cardList(
            OkraNFTAPI nftApi
    ) {
        return nftApi.getCardCollection("test_cards").data().orElseThrow();
    }

    @Bean
    public CustomLogger customLogger() {
        return new CustomLogger();
    }
}