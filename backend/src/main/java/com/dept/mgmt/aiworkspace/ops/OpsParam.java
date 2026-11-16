package com.dept.mgmt.aiworkspace.ops;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 系统操作工具参数定义（T-411）
 *
 * <p>声明式约束：LLM 抽取的参数在执行前逐项校验（必填/类型/枚举），
 * 校验失败的提示仅指向业务必需字段，不索要流程之外的无关信息。
 *
 * @author dept-mgmt
 */
@Data
@Builder
public class OpsParam {

    /** 参数名（与 executor 取参键一致） */
    private String name;

    /** 类型：string/number/date/bool */
    private String type;

    /** 是否必填 */
    private boolean required;

    /** 中文说明（注入 LLM 工具目录 + 校验提示） */
    private String desc;

    /** 枚举可选值（中文），非空时强校验 */
    private List<String> enums;

    /**
     * 实体解析标注（P0-4 参数语义校验）：
     * null=不过实体校验；"project"=项目名称实体；"user"=人员实体。
     * 执行前由 SystemOpsService.semanticValidate 做存在性/歧义校验并规范化参数值。
     */
    private String resolve;
}
