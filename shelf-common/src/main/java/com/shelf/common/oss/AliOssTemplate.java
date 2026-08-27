package com.shelf.common.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
public class AliOssTemplate {

    private final AliOssProperties properties;
    private final OSS ossClient;

    public AliOssTemplate(AliOssProperties properties) {
        this.properties = properties;
        this.ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
    }

    /**
     * 上传文件（指定完整路径）
     */
    public String upload(byte[] bytes, String objectName) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            ossClient.putObject(properties.getBucketName(), objectName, inputStream);
        } catch (Exception e) {
            log.error("OSS上传失败, objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
        return buildUrl(objectName);
    }

    /**
     * 便捷方法：自动生成UUID文件名
     */
    public String upload(byte[] bytes, String folder, String extension) {
        String objectName = folder + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        return upload(bytes, objectName);
    }

    private String buildUrl(String objectName) {
        String cleanEndpoint = properties.getEndpoint().replaceFirst("^(https?://)", "");
        return "https://" + properties.getBucketName() + "." + cleanEndpoint + "/" + objectName;
    }

    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS客户端已安全关闭");
        }
    }
}