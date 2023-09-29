package io.mindspice.itemserver.configuration;

import io.mindspice.databaseservice.client.DBServiceClient;
import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.itemserver.Settings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;


@Configuration
public class DbClientConfig {

    @Bean
    public DBServiceClient okraDBClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new DBServiceClient(Settings.get().okraDBUri, Settings.get().okraDBUser, Settings.get().okraDBPass);
    }

    @Bean
    public DBServiceClient chiaDBClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new DBServiceClient(Settings.get().chiaDBUri, Settings.get().chiaDBUser, Settings.get().chiaDBPass);
    }

    @Bean
    OkraChiaAPI okraChiaApi(@Qualifier("chiaDBClient") DBServiceClient chiaDBClient) {
        return new OkraChiaAPI(chiaDBClient);
    }

    @Bean
    OkraNFTAPI okraNFTApi(@Qualifier("okraDBClient") DBServiceClient okraDBClient) {
        return new OkraNFTAPI(okraDBClient);
    }

    @Bean
    OkraGameAPI okraGameApi(@Qualifier("okraDBClient") DBServiceClient okraDBClient) {
        return new OkraGameAPI(okraDBClient);
    }

}
