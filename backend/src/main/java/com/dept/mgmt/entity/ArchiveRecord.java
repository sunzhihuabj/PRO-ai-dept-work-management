package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 归档记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_record")
public class ArchiveRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long fileId;
    private String archiveType;
    private Integer archiveStatus;
    private String archiveBy;
    private LocalDateTime archiveTime;
    private String description;
}
