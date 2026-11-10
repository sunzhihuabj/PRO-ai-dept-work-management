package com.dept.mgmt.platform.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编码规则 DTO
 *
 * @author dept-mgmt
 */
@Data
public class CodeRuleDTO {

    private Long id;

    /** 规则编码（唯一）：PRJ/HT/WL... */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 50, message = "规则编码不能超过50字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "规则编码仅允许大写字母/数字/下划线，且以字母开头")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过100字符")
    private String ruleName;

    /**
     * 编码模板：{yyyy}/{yyyyMM}/{yyyyMMdd} 日期变量；{seq:n} 流水号左补零 n 位；{xxx} 调用方参数
     */
    @NotBlank(message = "编码模板不能为空")
    @Size(max = 100, message = "编码模板不能超过100字符")
    private String pattern;

    /** 流水号重置维度：yyyy / yyyyMM / 空=永不重置 */
    @Pattern(regexp = "^(yyyy|yyyyMM|yyyyMMdd)?$", message = "日期维度仅允许 yyyy / yyyyMM / yyyyMMdd 或留空")
    private String datePattern;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过200字符")
    private String remark;
}
