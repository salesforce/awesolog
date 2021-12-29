package com.salesforce.mce.awesolog;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.rolling.RolloverFailure;

public class S3FixedWindowRollingPolicy extends FixedWindowRollingPolicy {

    ExecutorService executor = Executors.newFixedThreadPool(1);

    String awsAccessKey;
    String awsSecretKey;
    String s3BucketName;
    String s3FolderName;
    String s3Region;

    boolean rollingOnExit = true;

    AmazonS3 s3Client;

    protected AmazonS3 getS3Client() {
        if (s3Client == null) {
            if (getAwsAccessKey() == null || getAwsSecretKey() == null || getS3Region() == null ) {
                s3Client = AmazonS3ClientBuilder.defaultClient();
            } else {
                BasicAWSCredentials awsCredentials =
                        new BasicAWSCredentials(getAwsAccessKey(), getAwsSecretKey());
                AWSStaticCredentialsProvider awsStaticCredentialsProvider =
                        new AWSStaticCredentialsProvider(awsCredentials);
                s3Client = AmazonS3ClientBuilder
                        .standard()
                        .withRegion(getS3Region())
                        .withForceGlobalBucketAccessEnabled(true)
                        .withCredentials(awsStaticCredentialsProvider)
                        .build();
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
                    getS3Client().putObject(getS3BucketName(), s3Key, file);
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
