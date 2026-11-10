package com.dept.mgmt.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跨域事件实体（T-108 事件总线）
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_domain_event")
public class SysDomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识（幂等键） */
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 载荷 JSON */
    private String payload;

    /** 投递状态（PENDING/CONSUMED/FAILED/DEAD） */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 消费备注 */
    private String consumerNote;

    /** 发布时间 */
    private LocalDateTime createTime;

    /** 消费完成时间 */
    private LocalDateTime consumedAt;
}
