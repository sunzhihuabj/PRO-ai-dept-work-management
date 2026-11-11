package com.dept.mgmt.fineperm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 权限分配请求 DTO（T-404）
 * 支持批量授予（effect=GRANT）/批量拒绝（effect=DENY）；批量移除走 DELETE 接口复用本结构
 *
 * @author dept-mgmt
 */
@Data
public class PermAssignDTO {

    /** 主体类型（DEPT部门维度 POSITION职位维度） */
    @NotBlank
    private String subjectType;

    /** 主体ID（部门ID/职位ID） */
    @NotNull
    private Long subjectId;

    /** 权限点编码列表（批量） */
    @NotEmpty
    private List<String> permCodes;

    /** 效果（GRANT授予 DENY拒绝） */
    @NotBlank
    private String effect;

    /** 备注（写入变更日志） */
    private String remark;
}
