package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件处理记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_handle")
public class EventHandle extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long eventId;
    private String handleType;
    private String handleDesc;
    private Long handlerId;
    private LocalDateTime handleTime;
    private String handleResult;
}
