package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.jxch.rpc.http.WalletAPI;
import io.mindspice.jxch.rpc.schemas.wallet.nft.NftInfo;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

//TODO make logging make logging follow existing practices for formatting

public class AvatarService implements Runnable{
    private final LinkedBlockingQueue<Pair<String, Integer>> conversionQueue = new LinkedBlockingQueue<>(10);
    private final WalletAPI walletAPI;
    private final OkraGameAPI gameApi;
    private final CustomLogger logger;
    private final S3Service s3Service;
    UnsafeHttpClient client = new UnsafeHttpClient(5_000, 5_000, 5_000);
    String uuid = UUID.randomUUID().toString();

    public AvatarService(WalletAPI monWalletApi, OkraGameAPI gameApi, S3Service s3Service, CustomLogger logger) {
        this.walletAPI = monWalletApi;
        this.gameApi = gameApi;
        this.s3Service = s3Service;
        this.logger = logger;
    }

    public void submit(Pair<String, Integer> nftLauncher) {
        conversionQueue.add(nftLauncher);
    }

    public void run() {

        List<Pair<String, Integer>> conversions = IntStream.range(0, Math.min(20, conversionQueue.size()))
                .mapToObj ((i) -> conversionQueue.poll()).filter(Objects::nonNull).toList();

        logger.logApp(this.getClass(), TLogLevel.INFO, "Starting ConversionJob: " + uuid
                + " for: " +  conversions);

        for (var nftLauncher : conversions) {
            try {
                NftInfo nftInfo = walletAPI.nftGetInfo(nftLauncher.first()).data().orElseThrow();
                List<String> uris = nftInfo.dataUris();
                byte[] imgBytes = getConvertedImage(uris);
                if (imgBytes == null || imgBytes.length > 1024 * 66) { continue; }
                s3Service.uploadBytes(nftLauncher.second() + ".png", imgBytes);
                gameApi.updatePlayerAvatar(nftLauncher.second(), "");
            } catch (Exception e) {
                logger.logApp(this.getClass(), TLogLevel.ERROR, " ConversionJob: " + uuid  + " | Failed getting nftInfo for:"
                        + nftLauncher + " | PlayerId: " + nftLauncher.second() + " | Message: " + e.getMessage());
            }
        }
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
                    + uuid  + " with:  " + uris);
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
        System.out.println("safe:" + safe);
        return safe;
    }

}
