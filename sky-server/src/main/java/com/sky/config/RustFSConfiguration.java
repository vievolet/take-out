package com.sky.config;

import com.sky.properties.RustFSProperties;
import com.sky.utils.RustFSUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于创建RustFSUtil对象
 */
@Configuration
@Slf4j
public class RustFSConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RustFSUtil rustFSUtil(RustFSProperties rustFSProperties){
        log.info("开始创建RustFS文件上传工具类对象：{}", rustFSProperties);
       return new RustFSUtil(rustFSProperties.getEndpoint(),
                      rustFSProperties.getAccessKey(),
                      rustFSProperties.getSecretKey(),
                      rustFSProperties.getBucketName());
    }
}
