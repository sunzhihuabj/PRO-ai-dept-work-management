package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("improvement_proposal")
public class ImprovementProposal extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private String title;
    private String proposalType;
    private Long proposerId;
    private String currentState;
    private String targetState;
    private String proposalContent;
    private Integer status;
    private Integer reviewResult;
    private String reviewComment;
}
