package com.dept.mgmt.mdm.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户主体库实体（T-103 主数据 / T-213 迁移载体）
 * 名称唯一 + 社会信用代码，合同建档"能选不填"数据源
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_customer")
public class Customer extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户名称（唯一） */
    private String customerName;

    /** 统一社会信用代码（18位，可空） */
    private String socialCreditCode;

    /** 别名1 */
    private String alias1;

    /** 别名2 */
    private String alias2;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 客户分级（战略/重点/普通） */
    private String level;

    /** 备注 */
    private String remark;
}
