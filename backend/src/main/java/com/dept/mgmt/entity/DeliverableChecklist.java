package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交付物检查清单表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deliverable_checklist")
public class DeliverableChecklist extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    private Long templateId;
    private String deliverableName;
    private Integer required;
    private String fileType;
    /** 是否已上传（0未上传 1已上传） */
    private Integer uploaded;
    /** 关联文件ID（档案归档模块 file_info） */
    private Long fileId;
    /** 是否已检查（0未检查 1已检查） */
    private Integer checked;
    private LocalDateTime checkTime;
    private String checkBy;
}
