package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门 DTO
 */
@Data
public class SysDeptDTO {

    /** 父部门ID（0 为顶级） */
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称最长100字符")
    private String deptName;

    private Integer orderNum;

    @Size(max = 50, message = "负责人最长50字符")
    private String leader;

    @Size(max = 20, message = "联系电话最长20字符")
    private String phone;

    @Size(max = 100, message = "邮箱最长100字符")
    private String email;

    private Integer status;
}
