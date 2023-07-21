package io.mindspice.itemserver;

import java.util.HashMap;
import java.util.List;


public class Settings {
    static Settings instance;

    /* Monitor */
    public volatile int startHeight;
    public volatile int heightBuffer;
    public volatile boolean isPaused;
    /* Mint */
    public volatile int xchWalletId;
    public volatile int didWalletId;
    public volatile int nftWalletId;
    public volatile String royaltyAddr;
    public long mempoolMaxCost;
    // Important, this must be hex
    public volatile String changeAddress;
    public volatile int royaltyPct;
    public volatile int maxFeePerCost;
    public volatile int minFeePerCost;
    public volatile int chunkSize;
    /* RPC */
    public volatile String rpcHost;
    public volatile String nodeCerts;
    public volatile String walletCerts;
    /* Database */
    public String psqlPath;
    public String psqlUser;
    public String psqlPass;
    public String sqlitePath;
    /* Remote Access */
    public int adminPort;
    public String adminPass;
    public String adminCert;
    /* Credential NFT Data */
    public String[] credImgURIs;
    public String credImgHash;
    public String[] credMetaUris;
    public String credMetaHash;
    public String[] credLicenseUris;
    public String credLicenseHash;
    /* Offers */
    public int offerLimit;
    public List<String> monitoredAddresses;
    public HashMap<String,String> assetLookupTable;
    /* Card Rarity */
    public int holoPct = 3;
    public int goldPct = 20;
    public int highLvl = 40;

    public static Settings get() {
        if (instance != null) {
            return instance;
        } else {
            //init();
        }
        return instance;
    }

}
