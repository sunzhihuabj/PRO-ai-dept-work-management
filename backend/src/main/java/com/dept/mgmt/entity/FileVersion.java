package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件版本表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_version")
public class FileVersion extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long fileId;
    private Integer versionNumber;
    private String versionLabel;
    private String filePath;
    private Long fileSize;
    private String changeDesc;
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;
}
