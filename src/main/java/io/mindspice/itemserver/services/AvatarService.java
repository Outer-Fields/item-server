package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.itemserver.util.CustomLogger;
import io.mindspice.itemserver.util.Utils;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.http.UnsafeHttpClient;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

//TODO make logging make logging follow existing practices for formatting


public class AvatarService {
    private final WalletAPI walletAPI;
    private final OkraGameAPI gameApi;
    private final CustomLogger logger;
    private final S3Service s3Service;
    UnsafeHttpClient client = new UnsafeHttpClient(10_000, 10_000, 10_000);
    String uuid = UUID.randomUUID().toString();

    public AvatarService(WalletAPI monWalletApi, OkraGameAPI gameApi, S3Service s3Service, CustomLogger logger) {
        this.walletAPI = monWalletApi;
        this.gameApi = gameApi;
        this.s3Service = s3Service;
        this.logger = logger;
    }

    public void submit(Pair<Integer, String> updateInfo) {
        logger.logApp(this.getClass(), TLogLevel.DEBUG, "Received avatar update playerId: " + updateInfo.first()
                + " | NFT: " + updateInfo.second());
        Thread.ofVirtual().start(updateTask(updateInfo.first(), updateInfo.second()));
    }

    public Runnable updateTask(int playerId, String nftLauncher) {

        return new Runnable() {
            @Override
            public void run() {
                try {

                    long lastUpdate = gameApi.getLastAvatarUpdate(playerId).data().orElseThrow();
                    if (Instant.now().getEpochSecond() - lastUpdate < 86400) {
                        logger.logApp(this.getClass(), TLogLevel.INFO, "Player Id: " + playerId +
                                " | Ignored avatar update: Too soon");
                        return;
                    }

                    NftInfo nftInfo =  Utils.nftGetInfoWrapper(walletAPI, nftLauncher);
                    List<String> uris = nftInfo.dataUris();
                    byte[] imgBytes = getConvertedImage(uris);
                    if (imgBytes == null || imgBytes.length > 1024 * 66) {
                        logger.logApp(this.getClass(), TLogLevel.INFO, "Player Id: " + playerId +
                                " | Ignored avatar update: Too large of file");
                        return;
                    }
                    s3Service.uploadBytes(playerId + ".png", imgBytes);
                    gameApi.updatePlayerAvatar(playerId, playerId + ".png");

                    logger.logApp(this.getClass(), TLogLevel.INFO, "Player Id: " + playerId +
                            " | Updated player avatar with: " + nftLauncher);
                } catch (Exception e) {
                    logger.logApp(this.getClass(), TLogLevel.ERROR, " | PlayerId: " + playerId +
                            " | Failed updating avatar with: " + nftLauncher +
                            " | Message: " + e.getMessage(), e);
                }
            }
        };
    }

    public byte[] getConvertedImage(List<String> uris) throws IOException {

        byte[] imgBytes = null;
        for (var uri : uris) {
            try {
                imgBytes = client.requestBuilder()
                        .address(uri)
                        .asGet()
                        .maxResponseSize(10 * 1024 * 1024)
                        .makeAndGetBytes();
                if (imgBytes != null) { break; }
            } catch (Exception ignored) {
            }
        }

        if (imgBytes == null) { return null; }
        ByteArrayInputStream imageByteStream = new ByteArrayInputStream(imgBytes);
        if (!checkSafeImage(new ByteArrayInputStream(imgBytes))) {
            logger.logApp(this.getClass(), TLogLevel.ERROR, "Abuse attempted in ConversionJob: "
                    + uuid + " with:  " + uris);
            throw new IllegalStateException("Validation Fail");
        }

        BufferedImage ogImage = ImageIO.read(imageByteStream);

        BufferedImage resizedImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(ogImage, 0, 0, 128, 128, null);
        g.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", outputStream);
        return outputStream.toByteArray();
    }

    private boolean checkSafeImage(InputStream input) throws IOException {
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(input);
        Iterator<ImageReader> iter = ImageIO.getImageReaders(imageInputStream);
        long maxSize = 2048L * 2048L;

        if (!iter.hasNext()) {
            imageInputStream.close();
            return false;
        }

        boolean safe = false;
        try {
            ImageReader reader = iter.next();
            reader.setInput(imageInputStream, true, true);

            long width = reader.getWidth(0);
            long height = reader.getHeight(0);

            safe = (height * width) <= maxSize;
        } finally {
            imageInputStream.close();
        }
        return safe;
    }

}
