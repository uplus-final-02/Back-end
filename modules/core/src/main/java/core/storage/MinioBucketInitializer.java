//package core.storage;
//
//import core.storage.config.StorageProperties;
//import io.minio.BucketExistsArgs;
//import io.minio.MakeBucketArgs;
//import io.minio.MinioClient;
//import jakarta.annotation.PostConstruct;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//public class MinioBucketInitializer {
//
//    private final MinioClient internalMinioClient;
//    private final StorageProperties props;
//
//    public MinioBucketInitializer(
//            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
//            StorageProperties props
//    ) {
//        this.internalMinioClient = internalMinioClient;
//        this.props = props;
//    }
//
//    @PostConstruct
//    public void init() {
//        try {
//            boolean exists = internalMinioClient.bucketExists(
//                    BucketExistsArgs.builder()
//                            .bucket(props.bucket())
//                            .build()
//            );
//
//            if (!exists) {
//                internalMinioClient.makeBucket(
//                        MakeBucketArgs.builder()
//                                .bucket(props.bucket())
//                                .build()
//                );
//                log.info("[MinIO] bucket created: {}", props.bucket());
//            } else {
//                log.info("[MinIO] bucket exists: {}", props.bucket());
//            }
//        } catch (Exception e) {
//            throw new StorageException("[MinIO] bucket init failed: " + props.bucket(), e);
//        }
//    }
//}