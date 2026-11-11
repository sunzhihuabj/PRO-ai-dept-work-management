package com.dept.mgmt.aftermarket.entity;

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
 * 巡检记录实体（T-304 W8 售后域）
 *
 * @author dept-mgmt
 */
@Data
@TableName("inspection_record")
public class InspectionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 类型（HALF_YEAR半年/ANNUAL年度） */
    private String inspectionType;

    /** 巡检日期 */
    private LocalDate inspectionDate;

    /** 巡检人 */
    private String inspector;

    /** 巡检内容 */
    private String content;

    /** 发现问题 */
    private String issues;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
