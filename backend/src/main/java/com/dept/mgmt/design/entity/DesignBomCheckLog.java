package com.dept.mgmt.design.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM 校验记录实体（T-116 AGT-11 输出留痕）
 *
 * @author dept-mgmt
 */
@Data
@TableName("design_bom_check_log")
public class DesignBomCheckLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long schemeId;

    /** 是否通过（0有缺陷 1通过） */
    private Integer passed;

    /** 校验明细数 */
    private Integer totalItems;

    /** 违规明细 JSON */
    private String resultJson;

    /** 校验方（AGT-11/MANUAL） */
    private String checker;

    /** 校验时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
