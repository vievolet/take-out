package com.sky.utils;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Data
@AllArgsConstructor
@Slf4j
public class RustFSUtil {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;

    /**
     * 文件上传（方法参数和阿里云完全一样）
     */
    public String upload(byte[] bytes, String objectName) {
        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );

            // 拼接路径
            String url = endpoint + "/" + bucketName + "/" + objectName;
            log.info("文件上传到:{}", url);
            return url;

        } catch (Exception e) {
            log.error("RustFS文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }
}