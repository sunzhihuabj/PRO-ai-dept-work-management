package com.dept.mgmt.event.model;

/**
 * 跨域事件类型枚举（T-108，规划 12 个事件）
 * 事件是跨域通信唯一通道：模块间禁止跨模块直读表
 *
 * @author dept-mgmt
 */
public enum DomainEventType {

    /** 中标（W1 投标域 → 建项目） */
    TENDER_WON("tender.won"),

    /** 合同生效（W2 → 触发设计域建档/预付款提醒） */
    CONTRACT_EFFECTIVE("contract.effective"),

    /** 收款到期（W2 → 提醒） */
    CONTRACT_PAYMENT_DUE("contract.payment.due"),

    /** 方案受控发布（W3 → 通知/档案） */
    DESIGN_RELEASED("design.released"),

    /** 齐套缺料（W4 → 请购建议） */
    KITTING_SHORTAGE("kitting.shortage"),

    /** 到货登记（W4 → 齐套复核） */
    MATERIAL_ARRIVED("material.arrived"),

    /** 验收通过（W6 → 收款提醒/档案归集） */
    DELIVERY_ACCEPTED("delivery.accepted"),

    /** 红灯升级（M1 看板 → 推送） */
    REDCARD_ESCALATED("redcard.escalated"),

    /** 变更生效（W5 → 批量准备） */
    ECN_CHANGED("ecn.changed"),

    /** 报修受理（W8 → 分级派单） */
    REPAIR_RECEIVED("repair.received"),

    /** 质保金到期（W8 → 提醒） */
    WARRANTY_DUE("warranty.due"),

    /** Agent 任务完成（T-107 → 通知/统计） */
    AGENT_TASK_FINISHED("agent.task.finished");

    private final String code;

    DomainEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
