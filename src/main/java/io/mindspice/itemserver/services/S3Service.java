package io.mindspice.itemserver.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;


import java.io.ByteArrayInputStream;

public class S3Service {
    @Autowired
    private AmazonS3 s3Client;

    private static final String BUCKET_NAME = "ofa-content";

    public void uploadBytes( String key, byte[] bytes) {
        // Wrap the byte array in a ByteArrayInputStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        // Set content length, so S3 knows how much data to expect
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("image/png");
        // Create a PutObjectRequest using the ByteArrayInputStream
        PutObjectRequest putRequest = new PutObjectRequest(BUCKET_NAME, key, inputStream, metadata);

        // Upload the data
        s3Client.putObject(putRequest);
    }
}
