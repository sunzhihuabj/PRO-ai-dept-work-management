package com.dept.mgmt.tender.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资格比对结果实体（T-301，AGT-02 产出）
 *
 * @author dept-mgmt
 */
@Data
@TableName("tender_qualification_check")
public class TenderQualificationCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 比对ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标讯ID */
    private Long tenderId;

    /** 招标要求项 */
    private String requirement;

    /** 比对结论（SATISFIED满足/NOT_SATISFIED不满足/UNCERTAIN待核实） */
    private String companyStatus;

    /** 比对依据（命中的公司资质或缺失说明） */
    private String evidence;

    /** 比对方 */
    private String checkedBy;

    /** 比对时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
