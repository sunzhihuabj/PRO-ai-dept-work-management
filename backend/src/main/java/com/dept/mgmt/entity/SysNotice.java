package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统通知公告表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String noticeTitle;
    /** 通知类型（1通知 2公告） */
    private Integer noticeType;
    private String noticeContent;
    /** 状态（0停用 1正常） */
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
}
