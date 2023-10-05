package io.mindspice.itemserver.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;


import java.io.ByteArrayInputStream;

public class S3Service {
    @Autowired
    private AmazonS3 s3Client;

    private static final String BUCKET_NAME = "avatar.okra.netwrok";

    public void uploadBytes( String key, byte[] bytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length); // Set content length, so S3 knows how much data to expect
        metadata.setContentType("image/png");
        metadata.setContentDisposition("inline");
        metadata.setCacheControl("max-age=14400"); // cloudflare cache for 2 hours
        PutObjectRequest putRequest = new PutObjectRequest(BUCKET_NAME, key, inputStream, metadata);
        s3Client.putObject(putRequest);
    }
}
