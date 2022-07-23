# awesolog

[![CircleCI](https://circleci.com/gh/salesforce/awesolog.svg?style=svg)](https://circleci.com/gh/salesforce/awesolog)

Policies for logback logger rolling appender that ships logs to S3. The program needs to be imported as a standard maven library.

The `S3FixedWindowRollingPolicy` is implemented that extends [`FixedWindowRollingPolicy`](http://logback.qos.ch/apidocs/ch/qos/logback/core/rolling/FixedWindowRollingPolicy.html) in Logback, therefore the behaviour is the same between the two except for uploading the log files to s3. 

Key values are used in default config (usually `application.conf`) before getting overridden with any corresponding key values set in `logback.xml`.

| Logback XML Key | Config Key |
| :----- | :-------- | 
| awsAccessKey | aws_access_key_id |
| awsSecretKey | aws_secret_access_key |
| awsAssumeRoleArn | aws_assume_role_arn |
| s3BucketName | s3_egress_bucket|
| s3FolderName | s3_log_folder_path |
| s3Region | aws_current_region |


## Logback XML

An example logback.xml that uses `S3FixedWindowRollingPolicy` with `RollingFileAppender`.

```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>/log/myapp.log</file>
    <encoder>
        <pattern>%date{yyyy-MM-dd HH:mm:ss ZZZZ} - %message%n%xException</pattern>
    </encoder>

    <!--
      Policy to upload a log file into S3 on log rolling or JVM exit.
      - On each log rolling, a rolled log file is created locally and uploaded to S3
      - When <rollingOnExit> is true, log rolling occurs on JVM exit and a rolled log is uploaded
      - When <rollingOnExit> is false, the active log file is uploaded as it is
    -->
    <rollingPolicy class="com.salesforce.mce.awesolog.S3FixedWindowRollingPolicy">
        <fileNamePattern>/log/myapp-log-%i.gz</fileNamePattern>
        <awsAccessKey>accesskey</awsAccessKey>
        <awsSecretKey>secretkey</awsSecretKey>
        <awsAssumeRoleArn>assumeRole</awsAssumeRoleArn>
        <s3BucketName>bucketName</s3BucketName>
        <s3FolderName>logs</s3FolderName>
        <rollingOnExit>true</rollingOnExit>
    </rollingPolicy>
    <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
        <maxFileSize>10KB</maxFileSize>
    </triggeringPolicy>
</appender>
```

## Default Config 

An example `application.conf` file that can be used to set the values.

Use AWS access key and secret access key for S3 Client, set below:

```conf
aws_access_key_id = xxxxxxxxxxxxx
aws_secret_access_key = xxxxxxxxxxxxx
```

To use AWS role to assume for S3 Client, set below:

```conf
aws_assume_role_arn = xxxxxxxxxxxxx
```

Following config values are mandatory:

```conf
s3_egress_bucket = bucketName
s3_log_folder_path = example/logs/
aws_current_region = region
```
