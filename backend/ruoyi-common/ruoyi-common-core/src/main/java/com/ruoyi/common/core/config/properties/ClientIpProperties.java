package com.ruoyi.common.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 客户端 IP 解析配置
 *
 * @author Savo Shen
 */
@Data
@ConfigurationProperties(prefix = "security.client-ip")
public class ClientIpProperties {

    /**
     * 是否采信反向代理注入的 X-Forwarded-For / X-Real-IP。
     * 仅当请求的 TCP 对端落在 trustedProxies 网段内时才会真正生效。
     */
    private Boolean trustProxyHeaders = true;

    /**
     * 按顺序尝试的代理请求头。
     * X-Real-IP 放前面：反向代理是无条件覆写它的（proxy_set_header X-Real-IP $remote_addr），
     * 客户端塞不进值；X-Forwarded-For 是追加语义，虽然按「从右往左取第一个非可信条目」
     * 也能拿到真实 IP，但前者更直接。
     */
    private List<String> headerNames = List.of("X-Real-IP", "X-Forwarded-For");

    /**
     * 可信代理网段。只有来自这些地址的请求，其代理头才会被采信。
     * 默认是回环加三段私有地址，覆盖 nginx 同机部署和 Docker 网桥两种情况。
     * 后端如果直接暴露在公网，这些网段不会命中，代理头自然被忽略。
     */
    private List<String> trustedProxies = List.of(
        "127.0.0.1/32",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16"
    );
}
