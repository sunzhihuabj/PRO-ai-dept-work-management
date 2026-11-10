package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 浪费记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("waste_record")
public class WasteRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    /** 浪费类型（等待/缺陷/搬运/过度加工/库存/动作/过量生产） */
    private String wasteType;
    private String wasteDesc;
    private String wasteSource;
    /** 严重程度（1高 2中 3低） */
    private Integer severity;
    /** 状态（0未处理 1处理中 2已消除 3已关闭） */
    private Integer status;
    private String improvementAction;
    private Long handlerId;
}
