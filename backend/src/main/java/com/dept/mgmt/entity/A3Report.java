package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a3_report")
public class A3Report extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long proposalId;
    private String title;
    private String background;
    private String currentCondition;
    private String goal;
    private String rootCause;
    private String countermeasures;
    private String checkResult;
    private String followUp;
    private Integer status;
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;
}
