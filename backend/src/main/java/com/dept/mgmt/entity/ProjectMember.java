package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目成员表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_member")
public class ProjectMember extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long userId;
    /** 项目角色（项目经理/技术负责人/成员等） */
    private String roleType;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    /** 状态（0已离开 1在项目中） */
    private Integer status;
}
