package com.ruoyi.common.satoken.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.util.Set;

/**
 * JWT 密钥的启动期校验。
 * <p>
 * 这里用的是 StpLogicJwtForSimple，token 是无状态 JWT：密钥一旦是可猜的，
 * 任何人都能自己签一个 admin 的 token，整套权限体系直接失效。
 * 所以宁可启动失败，也不能带着弱密钥跑起来。
 *
 * @author Savo Shen
 */
@Slf4j
@AutoConfiguration
public class JwtSecretValidator {

    /**
     * 最短长度。32 位十六进制（openssl rand -hex 32 输出 64 字符）绰绰有余，
     * 这里取一个不至于卡住合理配置的下限。
     */
    private static final int MIN_LENGTH = 32;

    /**
     * 历史上出现过的、或者一眼就能猜到的值
     */
    private static final Set<String> KNOWN_WEAK = Set.of(
        "abcdefghijklmnopqrstuvwxyz",
        "please_change_me_to_a_long_random_string",
        "replace-with-openssl-rand-hex-32",
        "123456",
        "secret",
        "changeme"
    );

    @Value("${sa-token.jwt-secret-key:}")
    private String jwtSecretKey;

    @PostConstruct
    public void validate() {
        String key = jwtSecretKey == null ? "" : jwtSecretKey.trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("""
                sa-token.jwt-secret-key 未配置。
                请在 backend/.env（或 .env.docker）中设置 JWT_SECRET_KEY，生成方式：
                    openssl rand -hex 32
                这是签发登录 token 的密钥，缺失或可猜会导致任何人都能伪造管理员身份。""");
        }
        if (KNOWN_WEAK.contains(key.toLowerCase())) {
            throw new IllegalStateException("""
                JWT_SECRET_KEY 仍然是示例值/默认值，这个值是公开的，必须更换。
                生成新值：openssl rand -hex 32
                注意：更换后所有已登录用户需要重新登录。""");
        }
        if (key.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET_KEY 长度不足（当前 " + key.length() + " 字符，至少需要 " + MIN_LENGTH + " 字符）。"
                    + "生成方式：openssl rand -hex 32");
        }
        log.info("JWT 密钥校验通过（长度 {} 字符）", key.length());
    }
}
