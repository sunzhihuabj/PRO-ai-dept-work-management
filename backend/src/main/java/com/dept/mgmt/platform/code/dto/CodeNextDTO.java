package com.dept.mgmt.platform.code.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 取号请求 DTO
 *
 * @author dept-mgmt
 */
@Data
public class CodeNextDTO {

    /** 规则编码 */
    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;

    /** 模板中自定义变量取值（如 type=PRJ），缺失时发号失败 */
    private Map<String, String> params;
}
