/*
 * Copyright (c) 2021, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.awesolog;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.rolling.RolloverFailure;

import com.salesforce.mce.awesolog.aws.S3ClientConstructor;

public class S3FixedWindowRollingPolicy extends FixedWindowRollingPolicy {

    ExecutorService executor = Executors.newFixedThreadPool(1);

    String s3BucketName;
    String s3FolderName;

    S3Client s3Client;

    boolean rollingOnExit = true;

    protected S3Client getS3Client() {
        if (s3Client == null) {
            S3ClientBuilder s3ClientBuilder = S3Client.builder();
            AwsCredentialsProvider awsCredentialsProvider = DefaultCredentialsProvider.create();
            s3ClientBuilder = s3ClientBuilder.credentialsProvider(awsCredentialsProvider);
            return s3ClientBuilder.build();
        } else {
            return s3Client;
        }
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
                    getS3Client().putObject(
                        PutObjectRequest
                            .builder()
                            .bucket(getS3BucketName())
                            .key(s3Key)
                            .build(),
                        RequestBody.fromInputStream(
                            new FileInputStream(file.getPath()),
                            file.length()
                        )
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

    public boolean isRollingOnExit() {
        return rollingOnExit;
    }

    public void setRollingOnExit(boolean rollingOnExit) {
        this.rollingOnExit = rollingOnExit;
    }
}
