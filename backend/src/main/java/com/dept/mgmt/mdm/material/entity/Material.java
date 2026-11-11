package com.dept.mgmt.mdm.material.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料主数据实体（一码一物）
 * 字段对齐 T-003《物料归一清单》模板 12 列口径
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mdm_material")
public class Material extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 物料编码（唯一，如 WL-000001） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specModel;

    /** 计量单位 */
    private String unit;

    /** 物料类别 */
    private String category;

    /** 旧编码/来源系统 */
    private String oldCode;

    /** 别名1 */
    private String alias1;

    /** 别名2 */
    private String alias2;

    /** 查重状态（未查重/疑似重复/已归并/唯一） */
    private String dedupStatus;

    /** 一码一物核验（待核验/通过/不通过） */
    private String verifyStatus;

    /** 标准成本价（元/单位，AGT-21 归集计算基础，T-303 补充） */
    private java.math.BigDecimal standardPrice;

    /** 备注 */
    private String remark;
}
