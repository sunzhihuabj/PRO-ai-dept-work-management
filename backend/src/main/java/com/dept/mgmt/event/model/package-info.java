/**
 * 跨域事件模型定义
 *
 * <p>职责：定义 12 个跨域事件的载荷结构，包括但不限于：
 * tender.won（中标）、contract.effective（合同生效）、contract.payment.due（收款到期）、
 * design.released（方案受控发布）、kitting.shortage（齐套缺料）、delivery.accepted（验收通过）、
 * redcard.escalated（红灯升级）、ecn.changed（变更生效）等。
 *
 * <p>对应规划任务：T-108。当前状态：骨架占位（Phase 0 / T-004）。
 */
package com.dept.mgmt.event.model;
