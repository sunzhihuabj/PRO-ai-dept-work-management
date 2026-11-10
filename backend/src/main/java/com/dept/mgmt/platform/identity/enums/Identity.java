package com.dept.mgmt.platform.identity.enums;

/**
 * 三身份枚举（T-102 权限模型）
 *
 * <p>语义（2026-11-10 用户确认）：
 * EXPERT  —— 人类：专家/骨干，可执行专业判断类操作，可被选为被代理人；
 * WORKER  —— 人类：普通执行者（用户未设置身份时的默认值，下限安全）；
 * ASSISTANT —— AI 助理虚拟身份：无登录账号，只能以被代理人名义执行，
 *              有效权限 = min(身份基线, 被代理人权限, 能力白名单)。
 *
 * @author dept-mgmt
 */
public enum Identity {

    /** 专家/骨干（人类） */
    EXPERT("EXPERT", "专家/骨干", true),

    /** 工作者（人类，默认身份） */
    WORKER("WORKER", "工作者", true),

    /** AI 助理（虚拟身份，无登录账号，仅代理执行） */
    ASSISTANT("ASSISTANT", "AI助理", false);

    private final String code;
    private final String label;
    private final boolean human;

    Identity(String code, String label, boolean human) {
        this.code = code;
        this.label = label;
        this.human = human;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否人类身份 */
    public boolean isHuman() {
        return human;
    }

    /**
     * 按 code 解析；null/未知返回 WORKER（下限安全）
     */
    public static Identity of(String code) {
        if (code == null || code.isBlank()) {
            return WORKER;
        }
        for (Identity identity : values()) {
            if (identity.code.equalsIgnoreCase(code.trim())) {
                return identity;
            }
        }
        return WORKER;
    }
}
