package com.dept.mgmt.chnchange.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 旧版资料回收任务实体（T-302，BR-07：旧版资料必须回收）
 *
 * @author dept-mgmt
 */
@Data
@TableName("ecn_recall_task")
public class EcnRecallTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** ECN ID */
    private Long ecnId;

    /** 项目ID */
    private Long projectId;

    /** 资料类型（SCHEME方案/BOM明细/DRAWING图纸/DOC文档） */
    private String itemType;

    /** 资料ID */
    private Long itemId;

    /** 资料描述（含版本标识） */
    private String itemDesc;

    /** 回收责任人 */
    private String owner;

    /** 状态（PENDING待回收/RECALLED已回收/CONFIRMED已确认） */
    private String status;

    /** 回收期限 */
    private LocalDate dueDate;

    /** 回收完成时间 */
    private LocalDateTime recalledAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
