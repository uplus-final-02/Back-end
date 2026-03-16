package org.backend.transcoder.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.transcode")
public class TranscodeConcurrencyProperties {
    private int maxConcurrentFfmpeg = 2;
}