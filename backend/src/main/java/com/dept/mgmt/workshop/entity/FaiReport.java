package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FAI 首件检验报告实体（T-201 W4，BR-02 数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("fai_report")
public class FaiReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单ID */
    private Long workorderId;

    /** 项目ID */
    private Long projectId;

    /** 结论（PASS/FAIL） */
    private String result;

    /** 检验员 */
    private String inspector;

    /** 检验时间 */
    private LocalDateTime checkedAt;

    /** 检验记录 */
    private String content;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
