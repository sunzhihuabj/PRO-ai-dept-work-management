package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件信息表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_info")
public class EventInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Integer eventType;
    private String eventTitle;
    private Integer urgency;
    private Integer status;
    private Long applicantId;
    private Long handlerId;
    private String description;
    private LocalDateTime closeTime;
}
