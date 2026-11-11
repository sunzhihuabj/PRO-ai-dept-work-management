package com.dept.mgmt.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档案实体（T-305 M3，电子实物双轨）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_info")
public class ArchiveInfo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 档号（DA 编码引擎） */
    private String archiveNo;

    /** 项目ID */
    private Long projectId;

    /** 类型（CONTRACT合同/SCHEME方案/DRAWING图纸/RECORD记录/ACCEPTANCE验收） */
    private String archiveType;

    /** 档案标题 */
    private String title;

    /** 载体（ELECTRONIC电子/PAPER纸质/DUAL双轨） */
    private String medium;

    /** 实物存放位置（纸质/双轨必填） */
    private String location;

    /** 状态（NORMAL正常/BORROWED借出/DESTROYED已鉴定销毁） */
    private String status;

    /** 备注 */
    private String remark;
}
