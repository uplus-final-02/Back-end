package org.backend.transcoder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TranscodeConcurrencyProperties.class)
public class TranscodeConcurrencyConfig {
}