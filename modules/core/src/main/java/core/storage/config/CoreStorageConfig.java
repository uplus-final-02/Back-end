package core.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CloudFrontProperties.class)
public class CoreStorageConfig {
}