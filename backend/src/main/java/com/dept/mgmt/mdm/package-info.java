/**
 * 主数据域 —— 建档取码与一码一物
 *
 * <p>原则：能选不填（主数据字段一律下拉+搜索，服务端拒绝自由文本，模糊匹配走 AGT-12）；
 * 一次录入多处引用（跨模块联动走 {@link com.dept.mgmt.event event} 事件总线，禁止人工跨模块补录）。
 *
 * <p>对应规划任务：T-103 主数据治理（客户主体库/风场库/物料库/项目档案 27 字段）；
 * T-104 治理 Agent（模糊匹配/查重聚类建议，TOP3 命中率 ≥90%）。
 * <p>子包说明：customer 客户主体库；material 物料库；windfarm 风场库；project 项目档案。
 *
 * <p>当前状态：骨架占位（Phase 0 / T-004），Phase 1 实施。
 */
package com.dept.mgmt.mdm;
