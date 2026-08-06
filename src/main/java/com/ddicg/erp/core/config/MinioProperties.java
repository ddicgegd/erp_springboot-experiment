package com.ddicg.erp.core.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(
        prefix = "integration.minio",
        ignoreUnknownFields = false
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MinioProperties {
    String accessKey;
    String secretKey;
    String url;
    String bucket;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

}
