package com.markingsystem.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CosProperties.class)
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "tencent.cos", name = "enabled", havingValue = "true")
    public COSClient cosClient(CosProperties properties) {
        if (isBlank(properties.getSecretId()) || isBlank(properties.getSecretKey())) {
            throw new IllegalStateException("腾讯云 COS 已启用，但未配置 TENCENT_COS_SECRET_ID / TENCENT_COS_SECRET_KEY");
        }
        COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, clientConfig);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
