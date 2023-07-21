package io.mindspice.itemserver.configuration;

import io.mindspice.databaseservice.client.DBServiceClient;
import io.mindspice.databaseservice.client.api.OkraChiaAPI;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;


@Configuration
public class DbClientConfig {
    // Database Service Client
    private DBServiceClient dbServiceClient;

    @PostConstruct
    public void init() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        dbServiceClient = new DBServiceClient("127.0.0.1", "user", "password"); // TODO make this load from file
    }

    @Bean
    OkraChiaAPI okraChiaApi() {
        return new OkraChiaAPI(dbServiceClient);
    }

    @Bean
    OkraNFTAPI okraNFTApi() {
        return new OkraNFTAPI(dbServiceClient);
    }

    @Bean
    OkraGameAPI okraGameApi() {
        return new OkraGameAPI(dbServiceClient);
    }
}
