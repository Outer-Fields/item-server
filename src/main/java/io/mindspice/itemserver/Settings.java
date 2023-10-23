package io.mindspice.itemserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.mindspice.jxch.rpc.schemas.wallet.nft.MetaData;

import java.io.File;
import java.io.IOException;
import java.util.List;


public class Settings {
    static Settings INSTANCE;

    /* Monitor */
    public volatile int startHeight;
    public volatile int heightBuffer;
    public volatile boolean isPaused;

    /* Database Config */
    public String okraDBUri;
    public String okraDBUser;
    public String okraDBPass;
    public String chiaDBUri;
    public String chiaDBUser;
    public String chiaDBPass;

    /* Internal Services */
    public String authServiceUri;
    public int authServicePort;
    public String authServiceUser;
    public String authServicePass;

    /* Card Rarity */
    public int holoPct = 3;
    public int goldPct = 20;
    public int highLvl = 40;
    public String currCollection;

    /* Config Paths */
    public String mainNodeConfig;
    public String mintWalletConfig;
    public String transactionWalletConfig;
    public String mintJobConfig;
    public String okraJobConfig;
    public String outrJobConfig;

    /* S3 */
    public String s3AccessKey;
    public String s3SecretKey;


    /* Assets */
    public String boosterAddress;
    public String boosterTail;
    public String starterAddress;
    public String starterTail;


    /* DID Mint */
    public String didMintToAddr;
    public List<String> didUris;
    public List<String> didMetaUris;
    public List<String> didLicenseUris;
    public String didHash;
    public String didMetaHash;
    public String didLicenseHash;

    /* Dispersal Limits */
    public int nftFlagAmount;
    public int okraFlagAmount;
    public int outrFlagAmount;



    static {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        File file = new File("config.yaml");

        try {
            INSTANCE = mapper.readValue(file, Settings.class);
        } catch (IOException e) {
            try {
                writeBlank();
            } catch (IOException ex) { throw new RuntimeException(ex); }
            throw new RuntimeException("Failed to read config file.", e);
        }
    }

    public static Settings get() {
        return INSTANCE;
    }

    public static MetaData getAccountMintMetaData() {
        return null;
    }

    public static void writeBlank() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        File yamlFile = new File("defaults.yaml");
        mapper.writeValue(yamlFile, new Settings());
    }


}
