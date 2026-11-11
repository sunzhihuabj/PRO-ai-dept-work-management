package com.dept.mgmt.fineperm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户职位分配请求 DTO（T-404）
 * FIXED 固定职位无有效期；TEMP 临时职位必须提供 start/end 且 end > start
 * 注意：userId 由 URL path 提供（Controller 填充），body 中无需携带
 *
 * @author dept-mgmt
 */
@Data
public class UserPositionDTO {

    /** 用户ID（由 URL path 提供，Controller 填充） */
    private Long userId;

    /** 职位ID */
    @NotNull
    private Long positionId;

    /** 职位性质（FIXED/TEMP） */
    @NotBlank
    private String positionKind;

    /** 生效开始时间（TEMP 必填） */
    private LocalDateTime startTime;

    /** 生效结束时间（TEMP 必填） */
    private LocalDateTime endTime;

    /** 备注（写入变更日志） */
    private String remark;
}
