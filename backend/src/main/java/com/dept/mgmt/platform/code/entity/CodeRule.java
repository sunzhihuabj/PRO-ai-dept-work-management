package com.dept.mgmt.platform.code.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编码规则实体
 * 配置化发号规则：pattern 模板 + date_pattern 流水号重置维度
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_code_rule")
public class CodeRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（唯一）：PRJ/HT/WL... */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /**
     * 编码模板，支持变量：
     * {yyyy}/{yyyyMM}/{yyyyMMdd} 系统日期；{seq:n} 流水号左补零 n 位；{xxx} 调用方自定义参数
     */
    private String pattern;

    /** 流水号重置维度的日期格式（yyyy/yyyyMM/空=永不重置），与 pattern 中日期变量对应 */
    private String datePattern;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 备注 */
    private String remark;
}
