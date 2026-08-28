package com.ruoyi.common.core.utils.ip;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端真实 IP 解析。
 * <p>
 * 与 Hutool 默认实现的区别：<b>不无条件信任 X-Forwarded-For 等代理请求头</b>。
 * 这些头由客户端自由构造，如果直接采信，任何按 IP 计数的逻辑（登录失败锁定、接口限流）
 * 都可以靠每次换一个头值来绕过。
 * <p>
 * 这里的规则是：
 * <ol>
 *     <li>只有当 TCP 对端（{@code request.getRemoteAddr()}）本身落在可信代理网段内时，
 *     才会去读代理请求头；直连的请求一律以对端地址为准。</li>
 *     <li>读 X-Forwarded-For 时从右往左找，跳过属于可信代理的条目，
 *     第一个非可信条目就是真实客户端 —— 右侧条目是自己的反向代理追加的，无法伪造。</li>
 * </ol>
 *
 * @author Savo Shen
 */
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_V4 = "127.0.0.1";

    private static volatile boolean trustProxyHeaders = false;
    private static volatile List<String> headerNames = Collections.emptyList();
    private static volatile List<CidrRange> trustedProxies = Collections.emptyList();

    private ClientIpResolver() {
    }

    /**
     * 由 ClientIpConfig 在启动时注入配置
     */
    public static void configure(boolean trustProxyHeaders, List<String> headerNames, List<String> trustedProxies) {
        List<CidrRange> ranges = new ArrayList<>();
        if (trustedProxies != null) {
            for (String cidr : trustedProxies) {
                CidrRange range = CidrRange.parse(cidr);
                if (range != null) {
                    ranges.add(range);
                }
            }
        }
        ClientIpResolver.trustProxyHeaders = trustProxyHeaders;
        ClientIpResolver.headerNames = headerNames == null ? Collections.emptyList() : List.copyOf(headerNames);
        ClientIpResolver.trustedProxies = List.copyOf(ranges);
    }

    /**
     * 解析请求的客户端 IP
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String remoteAddr = normalize(request.getRemoteAddr());
        if (!trustProxyHeaders || !isTrustedProxy(remoteAddr)) {
            // 直连，或者对端不在可信代理网段内：代理头一律不采信
            return remoteAddr;
        }
        for (String headerName : headerNames) {
            String candidate = fromHeader(request.getHeader(headerName));
            if (candidate != null) {
                return candidate;
            }
        }
        return remoteAddr;
    }

    /**
     * 从一个代理头里取真实客户端：从右往左，第一个不属于可信代理的条目
     */
    private static String fromHeader(String headerValue) {
        if (isBlank(headerValue)) {
            return null;
        }
        String[] parts = headerValue.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i].trim());
            if (isBlank(candidate) || UNKNOWN.equalsIgnoreCase(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isTrustedProxy(String ip) {
        if (isBlank(ip)) {
            return false;
        }
        for (CidrRange range : trustedProxies) {
            if (range.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IPv6 形式的回环统一成 127.0.0.1，避免可信网段配置要写两份
     */
    private static String normalize(String ip) {
        if (isBlank(ip)) {
            return UNKNOWN;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return LOCALHOST_V4;
        }
        // ::ffff:192.168.1.4 这类 IPv4-mapped 地址还原成 IPv4
        int mapped = ip.lastIndexOf(':');
        if (ip.startsWith("::ffff:") && mapped >= 0 && ip.indexOf('.') > 0) {
            return ip.substring(mapped + 1);
        }
        return ip;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 一个 IPv4 网段。非 IPv4 的配置退化为字符串全等比较，够用且不引入额外依赖。
     */
    private record CidrRange(long network, long mask, String literal) {

        static CidrRange parse(String cidr) {
            if (cidr == null || cidr.isBlank()) {
                return null;
            }
            String value = cidr.trim();
            int slash = value.indexOf('/');
            String addr = slash < 0 ? value : value.substring(0, slash);
            int prefix = 32;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
                if (prefix < 0 || prefix > 32) {
                    return null;
                }
            }
            Long ip = toLong(addr);
            if (ip == null) {
                // IPv6 等：按字面量精确匹配
                return new CidrRange(0L, 0L, addr);
            }
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return new CidrRange(ip & mask, mask, null);
        }

        boolean contains(String ip) {
            if (literal != null) {
                return literal.equalsIgnoreCase(ip);
            }
            Long value = toLong(ip);
            return value != null && (value & mask) == network;
        }

        private static Long toLong(String ip) {
            if (ip == null) {
                return null;
            }
            String[] segments = ip.split("\\.");
            if (segments.length != 4) {
                return null;
            }
            long result = 0L;
            for (String segment : segments) {
                int part;
                try {
                    part = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (part < 0 || part > 255) {
                    return null;
                }
                result = (result << 8) | part;
            }
            return result;
        }
    }
}
