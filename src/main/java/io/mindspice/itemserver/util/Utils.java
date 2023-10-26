package io.mindspice.itemserver.util;

import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.ApiResponse;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.rpc.util.RPCException;

import java.util.function.Function;


public class Utils {

    public static NftInfo nftGetInfoWrapper(WalletAPI walletAPI, String coinId) throws RPCException, InterruptedException {
        int i = 100;
        Thread.sleep(10);
        ApiResponse<NftInfo> info = walletAPI.nftGetInfo(coinId);
        while(!info.success() && i > 0) {
            Thread.sleep(50);
            info = walletAPI.nftGetInfo(coinId);
            i--;
        }
        if (info.success()) {
            return info.data().get();
        } else {
            throw new IllegalStateException("Failed to get nft info after 20 tries");
        }
    }

    public static String uidFromUrl(String url) {
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1];
        return lastPart.replace(".png", "");
    }

}
