package io.mindspice.itemserver.configuration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.mindspice.itemserver.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class S3Config {

    @Bean
    public AmazonS3 s3Client() {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(Settings.get().s3AccessKey, Settings.get().s3SecretKey);
        return AmazonS3ClientBuilder.standard()
                .withRegion("us-east-1")
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .build();
    }
}