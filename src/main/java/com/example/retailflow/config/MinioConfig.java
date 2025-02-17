package com.example.retailflow.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(@Value("${app.storage.minio.endpoint}") String endpoint,
                                   @Value("${app.storage.minio.access-key}") String accessKey,
                                   @Value("${app.storage.minio.secret-key}") String secretKey,
                                   @Value("${app.storage.minio.bucket}") String bucket) throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("created minio bucket: {}", bucket);
        }

        String publicReadPolicy = """
                {
                  "Version":"2012-10-17",
                  "Statement":[
                    {
                      "Effect":"Allow",
                      "Principal":{"AWS":["*"]},
                      "Action":["s3:GetObject"],
                      "Resource":["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
        client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(publicReadPolicy).build());
        return client;
    }
}
