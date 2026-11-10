package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件信息表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    /** 来源任务ID（任务中上传的资料） */
    private Long taskId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String version;
    private Integer versionNumber;
    private Integer isArchive;
    private LocalDateTime archiveTime;
    private String description;
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;
}
