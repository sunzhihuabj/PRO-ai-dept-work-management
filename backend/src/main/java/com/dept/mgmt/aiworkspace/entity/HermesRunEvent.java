package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Hermes run 执行过程事件实体（B3 过程回写）
 *
 * <p>事件来源：Hermes API Server GET /v1/runs/{id}/events SSE 流；
 * 由 HermesDelegateService 后台线程消费落库，(run_id, seq) 幂等去重，
 * 供 AI 助手前端按 runId 轮询绘制执行时间线（B5 审批联动亦以此为数据源）。
 *
 * @author dept-mgmt
 */
@Data
@TableName("hermes_run_event")
public class HermesRunEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Hermes runId（与 ai_message.content 内嵌标记关联） */
    private String runId;

    /** Hermes 事件序号（SSE seq，-1 表示缺失） */
    private Long seq;

    /** 事件类型（tool.started/tool.completed/approval.request/run.*等） */
    private String eventType;

    /** 工具名（tool.* 事件） */
    private String tool;

    /** 内容预览（工具参数预览/中间文本/错误信息，截断 500 字符） */
    private String preview;

    /** 工具执行耗时毫秒（tool.completed 事件） */
    private Long durationMs;

    /** 工具是否执行失败（tool.completed.error） */
    private Integer isError;

    /** 落库时间 */
    private LocalDateTime createTime;
}
