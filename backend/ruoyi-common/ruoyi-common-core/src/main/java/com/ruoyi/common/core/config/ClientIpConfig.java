package com.ruoyi.common.core.config;

import com.ruoyi.common.core.config.properties.ClientIpProperties;
import com.ruoyi.common.core.utils.ip.ClientIpResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 把客户端 IP 解析配置推给静态的 ClientIpResolver
 *
 * @author Savo Shen
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {

    private final ClientIpProperties properties;

    @PostConstruct
    public void init() {
        ClientIpResolver.configure(
            Boolean.TRUE.equals(properties.getTrustProxyHeaders()),
            properties.getHeaderNames(),
            properties.getTrustedProxies()
        );
    }
}
