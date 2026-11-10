package com.dept.mgmt.agent.enums;

/**
 * Agent 能力自主级别（T-110 四级状态机）
 *
 * <p>升级路径：DISABLED → SUGGESTION → AUTO → AUTO_BATCH；
 * 升级条件（默认，可调）：近周期采纳率 ≥90% 且执行次数 ≥10；
 * 降级条件：周期错误率 >10%（任何一周）→ 自动降一级并通知管理员；
 * 红线：is_never=1 能力点（审批/资金/主数据建档/删除）策略封顶 SUGGESTION，永不自动。
 *
 * @author dept-mgmt
 */
public enum AutonomyLevel {

    /** 0 禁用：能力不自动执行，也不生成建议 */
    DISABLED(0, "禁用"),

    /** 1 建议级：执行前强制人工确认（72h TTL 语义） */
    SUGGESTION(1, "建议级"),

    /** 2 自动级：自动执行，结果 24h 窗口内可回滚/反馈 */
    AUTO(2, "自动级"),

    /** 3 批量自动级：自动执行且支持批量触发（预留） */
    AUTO_BATCH(3, "批量自动级");

    private final int code;
    private final String label;

    AutonomyLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AutonomyLevel of(Integer code) {
        if (code == null) {
            return SUGGESTION;
        }
        for (AutonomyLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        return SUGGESTION;
    }
}
