package com.campus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "campus.rustfs")
@Data
public class RustFSProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
}
