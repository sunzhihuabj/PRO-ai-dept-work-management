package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 事件处理 DTO
 */
@Data
public class EventHandleDTO {

    /** 处理类型：受理/驳回/转派/处理/完成/关闭 */
    @NotBlank(message = "处理类型不能为空")
    private String handleType;

    /** 处理描述 */
    private String handleDesc;

    /** 处理结果 */
    private String handleResult;

    /** 转派目标用户ID（仅在 handleType=转派 时使用） */
    private Long transferTo;
}
