package com.ruoyi.wms.ai.tool;

import cn.dev33.satoken.stp.StpUtil;

import java.util.Set;

/**
 * 一次 Agent 运行期间的权限快照。
 * <p>
 * 流式对话跑在工作线程里，Sa-Token 的登录态是请求线程的 ThreadLocal，工作线程里
 * 直接问 {@code StpUtil.hasPermission} 是拿不到的。所以在请求线程里先把工具会用到的
 * 权限逐个判定好，装进这个快照带进工作线程；工具执行时只查快照。
 * 没有快照（同步接口、单元测试）时退回直接问 Sa-Token。
 *
 * @author Savo
 */
public final class AiToolContext {

    private static final ThreadLocal<Set<String>> GRANTED = new ThreadLocal<>();

    private AiToolContext() {
    }

    public static void set(Set<String> granted) {
        GRANTED.set(granted);
    }

    public static void clear() {
        GRANTED.remove();
    }

    /** 当前用户是否拥有该权限；permission 为 null 视为无需权限 */
    public static boolean has(String permission) {
        if (permission == null) {
            return true;
        }
        Set<String> granted = GRANTED.get();
        if (granted != null) {
            return granted.contains(permission);
        }
        try {
            return StpUtil.hasPermission(permission);
        } catch (Exception e) {
            // 没有登录上下文（比如线程不对）一律按无权处理，宁可少做不可越权
            return false;
        }
    }
}
