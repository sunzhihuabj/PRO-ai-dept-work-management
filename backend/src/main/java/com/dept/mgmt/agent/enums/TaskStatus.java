package com.dept.mgmt.agent.enums;

/**
 * Agent 任务状态机（T-107）
 * queued → running → pending_confirm → done / failed / escalated / cancelled / rolled_back
 *
 * @author dept-mgmt
 */
public enum TaskStatus {

    /** 排队中 */
    QUEUED("queued"),

    /** 执行中 */
    RUNNING("running"),

    /** 待人工确认（建议级操作 / 未能自动执行） */
    PENDING_CONFIRM("pending_confirm"),

    /** 已完成 */
    DONE("done"),

    /** 执行失败 */
    FAILED("failed"),

    /** 升级人工处理 */
    ESCALATED("escalated"),

    /** 已取消 */
    CANCELLED("cancelled"),

    /** 已回滚 */
    ROLLED_BACK("rolled_back");

    private final String code;

    TaskStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 终态判断（终态任务不再被调度） */
    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELLED || this == ROLLED_BACK;
    }
}
