package org.backend.userapi.health;

import core.storage.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minioDependency")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient internalMinioClient;
    private final StorageProperties storageProperties;

    public MinioHealthIndicator(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            StorageProperties storageProperties
    ) {
        this.internalMinioClient = internalMinioClient;
        this.storageProperties = storageProperties;
    }

    @Override
    public Health health() {
        try {
            String bucket = storageProperties.bucket();
            boolean exists = internalMinioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());

            if (exists) {
                return Health.up()
                        .withDetail("dependency", "minio")
                        .withDetail("bucket", bucket)
                        .build();
            }

            return Health.down()
                    .withDetail("dependency", "minio")
                    .withDetail("bucket", bucket)
                    .withDetail("reason", "bucket does not exist")
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("dependency", "minio")
                    .withDetail("bucket", storageProperties.bucket())
                    .build();
        }
    }
}
