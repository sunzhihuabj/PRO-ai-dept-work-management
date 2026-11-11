package com.dept.mgmt.tender.entity;

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
 * 公司资质清单实体（T-301，AGT-02 比对基准）
 *
 * @author dept-mgmt
 */
@Data
@TableName("company_qualification")
public class CompanyQualification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资质ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资质名称 */
    private String qualName;

    /** 证书编号 */
    private String certNo;

    /** 有效期至 */
    private LocalDate validUntil;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
