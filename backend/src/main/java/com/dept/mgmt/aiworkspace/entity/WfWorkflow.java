package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务流程定义实体（T-408）
 * graph JSON 结构：{nodes:[{key,name,type,x,y,config}],edges:[{from,to,label}]}
 * 节点类型：start（开始）/ agent（能力执行）/ approval（人工审批）/ notification（通知）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_workflow")
public class WfWorkflow extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程编码（唯一，WF 规则） */
    private String wfCode;

    /** 流程名称 */
    private String name;

    /** 流程说明 */
    private String description;

    /** 流程图 JSON */
    private String graph;

    /** 状态（draft草稿/published已发布/offline已下线） */
    private String status;

    /** 版本号 */
    private Integer version;

    /** 创建人 */
    private String createBy;
}
