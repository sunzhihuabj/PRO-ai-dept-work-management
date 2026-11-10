package com.dept.mgmt.board.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 红灯规则配置实体（T-209 M1 看板，规则配置化：指标/比较符/阈值/级别/升级天数全部存库）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("redcard_rule")
public class RedcardRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 所属板编码（CONTRACT/DESIGN/KITTING/PRODUCE/SHIP/ACCEPT/TPLUS/RECTIFY/OVERVIEW） */
    private String board;

    /** 指标 key（BoardsService 指标字典） */
    private String metric;

    /** 比较符（GT/LT/EQ） */
    private String comparator;

    /** 阈值 */
    private BigDecimal threshold;

    /** 级别（RED红灯/YELLOW黄灯） */
    private String level;

    /** 超期升级天数（OPEN 超 N 天未处理升级推送） */
    private Integer escalateDays;

    /** 启用（0/1） */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
