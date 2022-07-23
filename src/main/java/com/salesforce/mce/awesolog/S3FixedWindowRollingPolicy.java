/*
 * Copyright (c) 2021, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.awesolog;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClientBuilder;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.rolling.RolloverFailure;

public class S3FixedWindowRollingPolicy extends FixedWindowRollingPolicy {

    ExecutorService executor = Executors.newFixedThreadPool(1);

    String awsAccessKey;
    String awsSecretKey;
    String awsAssumeRoleArn;
    String s3BucketName;
    String s3FolderName;
    String s3Region;

    boolean rollingOnExit = true;

    S3Client s3Client;

    protected S3Client getS3Client() {

        if (s3Client == null) {

            S3ClientBuilder s3ClientBuilder;
            StsClientBuilder stsClientBuilder;

            StaticCredentialsProvider staticCredentialsProvider = null;
            StsClient stsClient = null;

            if (getS3Region() != null) {
                Region region = Region.of(getS3Region());
                s3ClientBuilder = S3Client.builder().region(region);
                stsClientBuilder = StsClient.builder().region(region);
            } else {
                s3ClientBuilder = S3Client.builder();
                stsClientBuilder = StsClient.builder();
            }

            if (getAwsAccessKey() != null && getAwsSecretKey() != null) {
                staticCredentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(getAwsAccessKey(), getAwsSecretKey())
                );
            }

            if (staticCredentialsProvider != null) {
                stsClient = stsClientBuilder
                    .credentialsProvider(staticCredentialsProvider)
                    .build();
            }

            if (getAwsRoleToAssume() != null) {

                AssumeRoleRequest assumeRoleRequest =
                    AssumeRoleRequest
                        .builder()
                        .roleArn(getAwsRoleToAssume())
                        .roleSessionName("AwesologSession")
                        .build();

                StsAssumeRoleCredentialsProvider stsAssumeRoleCredentials =
                    StsAssumeRoleCredentialsProvider
                        .builder()
                        .refreshRequest(assumeRoleRequest)
                        .stsClient(stsClient)
                        .build();

                s3Client = s3ClientBuilder
                    .credentialsProvider(stsAssumeRoleCredentials)
                    .build();

            } else {

                if (staticCredentialsProvider != null) {
                    s3Client = s3ClientBuilder
                            .credentialsProvider(staticCredentialsProvider)
                            .build();
                } else {
                    s3Client = s3ClientBuilder.build();
                }
            }

        }
        return s3Client;
    }

    @Override
    public void start() {
        super.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHookRunnable()));
    }

    @Override
    public void rollover() throws RolloverFailure {
        super.rollover();
        FileNamePattern fileNamePattern = new FileNamePattern(this.fileNamePatternStr, this.context);
        String rolledLogFileName = fileNamePattern.convertInt(getMinIndex());
        uploadFileToS3Async(rolledLogFileName);
    }

    protected void uploadFileToS3Async(String filename) {
        final File file = new File(filename);
        if (file.exists() && file.length() != 0) {
            long snapshot = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            String s3Key = getS3FolderName() + "/day=" + LocalDate.now() + "/" + snapshot + "-" + file.getName();

            addInfo("Uploading " + filename);
            Runnable uploader = () -> {
                try {
                    PutObjectRequest putObjectRequest = PutObjectRequest
                        .builder()
                        .bucket(getS3BucketName())
                        .key(s3Key)
                        .build();
                    getS3Client().putObject(
                        putObjectRequest,
                        RequestBody.fromFile(file)
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            };
            executor.execute(uploader);
        } else {
            addError("File " + filename + " doesn't exist");
        }
    }

    class ShutdownHookRunnable implements Runnable {
        @Override
        public void run() {
            try {
                if (isRollingOnExit()) {
                    rollover(); // do rolling and upload
                } else {
                    uploadFileToS3Async(getActiveFileName()); // upload only
                }
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.MINUTES);
            } catch (Exception ex) {
                addError("Failed to upload a log in S3", ex);
                executor.shutdownNow();
            }
        }

    }

    private static String stripSuffix(String input, String suffix) {
        return (input == null || input.length() == 0)
                ? null
                : (input.endsWith(suffix) ? input.substring(0, input.length() - 1) : input);
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    public String getAwsRoleToAssume() {
        return awsAssumeRoleArn;
    }

    public void setAwsRoleToAssume(String awsAssumeRoleArn) {
        this.awsAssumeRoleArn = awsAssumeRoleArn;
    }

    public String getS3BucketName() {
        return s3BucketName;
    }

    public void setS3BucketName(String s3BucketName) {
        this.s3BucketName = s3BucketName;
    }

    public String getS3FolderName() {
        return stripSuffix(s3FolderName, "/");
    }

    public void setS3FolderName(String s3FolderName) {
        this.s3FolderName = s3FolderName;
    }

    public String getS3Region() {
        return s3Region;
    }

    public void setS3Region(String s3Region) {
        this.s3Region = s3Region;
    }

    public boolean isRollingOnExit() {
        return rollingOnExit;
    }

    public void setRollingOnExit(boolean rollingOnExit) {
        this.rollingOnExit = rollingOnExit;
    }
}
