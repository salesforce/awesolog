package com.salesforce.mce.awesolog.aws;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointParams;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class S3ClientConstructor {

    static final String ASSUME_ROLE_SESSION_NAME = "AwesologSession";

    private String awsAccessKey;

    private String awsSecretKey;

    private String awsSessionToken;

    private String awsAssumeRoleArn;

    private String s3Region;

    private String s3Endpoint;

    public S3ClientConstructor(
        String awsAccessKey,
        String awsSecretKey,
        String awsSessionToken,
        String awsAssumeRoleArn,
        String s3Region,
        String s3Endpoint
        ) {
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.awsSessionToken = awsSessionToken;
        this.awsAssumeRoleArn = awsAssumeRoleArn;
        this.s3Region = s3Region;
        this.s3Endpoint = s3Endpoint;
    }

    public S3Client construct() {

        S3Client s3Client = null;
        StsClient stsClient = null;

        S3ClientBuilder s3ClientBuilder = S3Client.builder();
        StsClientBuilder stsClientBuilder = StsClient.builder();

        StaticCredentialsProvider staticCredentialsProvider = null;

        if (s3Region != null) {
            Region region = Region.of(s3Region);
            s3ClientBuilder = s3ClientBuilder.region(region).forcePathStyle(true);
            stsClientBuilder = stsClientBuilder.region(region);
        }

        if( s3Endpoint != null ) {
            URI s3EndpointUri = URI.create(s3Endpoint);
            s3ClientBuilder = s3ClientBuilder.endpointOverride(s3EndpointUri);
            stsClientBuilder = stsClientBuilder.endpointOverride(s3EndpointUri);
        }

        if (awsAccessKey != null && awsSecretKey != null) {
            AwsCredentials awsCredentials;
            if (awsSessionToken != null) {
                awsCredentials = AwsSessionCredentials.create(awsAccessKey, awsSecretKey, awsSessionToken);
            } else {
                awsCredentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
            }
            staticCredentialsProvider = StaticCredentialsProvider.create(awsCredentials);
        }


        if (staticCredentialsProvider != null) {
            stsClient = stsClientBuilder
                    .credentialsProvider(staticCredentialsProvider)
                    .build();
        }

        if (awsAssumeRoleArn != null ) {

            AssumeRoleRequest assumeRoleRequest =
                    AssumeRoleRequest
                            .builder()
                            .roleArn(awsAssumeRoleArn)
                            .roleSessionName(ASSUME_ROLE_SESSION_NAME)
                            .build();

            StsAssumeRoleCredentialsProvider stsAssumeRoleCredentials =
                    StsAssumeRoleCredentialsProvider
                            .builder()
                            .refreshRequest(assumeRoleRequest)
                            .stsClient(stsClient)
                            .build();

            s3ClientBuilder = s3ClientBuilder.credentialsProvider(stsAssumeRoleCredentials);

        } else {

            if (staticCredentialsProvider != null) {
                s3ClientBuilder = s3ClientBuilder
                        .credentialsProvider(staticCredentialsProvider);
            }

        }

        s3Client = s3ClientBuilder.build();

        return s3Client;
    }
}
