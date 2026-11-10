package com.dept.mgmt.platform.code.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.CodeRuleMapper;
import com.dept.mgmt.platform.code.dto.CodeRuleDTO;
import com.dept.mgmt.platform.code.entity.CodeRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编码规则管理服务（CRUD + 校验）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeRuleService {

    /** 与 CodeEngineService 一致的模板变量语法 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)(?::(\\d+))?}");

    private final CodeRuleMapper codeRuleMapper;

    /**
     * 规则分页查询
     */
    public PageResult<CodeRule> list(int pageNum, int pageSize, String ruleCode, String ruleName) {
        LambdaQueryWrapper<CodeRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(ruleCode), CodeRule::getRuleCode, ruleCode);
        wrapper.like(StringUtils.hasText(ruleName), CodeRule::getRuleName, ruleName);
        wrapper.orderByAsc(CodeRule::getRuleCode);
        return PageResult.of(codeRuleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 创建规则（校验模板合法性与唯一性）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(CodeRuleDTO dto) {
        validatePattern(dto.getPattern(), dto.getDatePattern());
        if (codeRuleMapper.selectCount(new LambdaQueryWrapper<CodeRule>()
                .eq(CodeRule::getRuleCode, dto.getRuleCode())) > 0) {
            throw new BusinessException(409, "规则编码已存在: " + dto.getRuleCode());
        }
        CodeRule rule = new CodeRule();
        applyDto(rule, dto);
        rule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        codeRuleMapper.insert(rule);
        log.info("创建编码规则: {}", dto.getRuleCode());
        return rule.getId();
    }

    /**
     * 更新规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CodeRuleDTO dto) {
        CodeRule existing = codeRuleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "编码规则不存在");
        }
        validatePattern(dto.getPattern(), dto.getDatePattern());
        // ruleCode 不允许修改（序号表以 rule_code 关联）
        if (!existing.getRuleCode().equals(dto.getRuleCode())) {
            throw new BusinessException(400, "规则编码不允许修改");
        }
        applyDto(existing, dto);
        codeRuleMapper.updateById(existing);
        log.info("更新编码规则: {}", existing.getRuleCode());
    }

    /**
     * 删除规则（逻辑删除；已产生的序号记录保留）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CodeRule existing = codeRuleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "编码规则不存在");
        }
        codeRuleMapper.deleteById(id);
        log.info("删除编码规则: {}", existing.getRuleCode());
    }

    /**
     * 模板合法性校验：必须含 {seq} 变量；自定义变量与日期维度匹配
     */
    private void validatePattern(String pattern, String datePattern) {
        boolean hasSeq = false;
        Matcher m = VAR_PATTERN.matcher(pattern);
        while (m.find()) {
            String name = m.group(1);
            if ("seq".equals(name)) {
                hasSeq = true;
                String digits = m.group(2);
                if (digits == null || Integer.parseInt(digits) < 1 || Integer.parseInt(digits) > 10) {
                    throw new BusinessException(400, "流水号位数格式非法，应为 {seq:n}（1≤n≤10）");
                }
            } else if (!"yyyy".equals(name) && !"yyyyMM".equals(name) && !"yyyyMMdd".equals(name)) {
                throw new BusinessException(400, "模板含未支持的变量 {" + name + "}，"
                        + "仅允许 {yyyy}/{yyyyMM}/{yyyyMMdd}/{seq:n}（自定义参数在发号时传入，模板中不允许出现）");
            }
        }
        if (!hasSeq) {
            throw new BusinessException(400, "编码模板必须包含 {seq:n} 流水号变量");
        }
        boolean hasDateVar = pattern.contains("{yyyy");
        if (StringUtils.hasText(datePattern) && !hasDateVar) {
            throw new BusinessException(400, "设置了日期重置维度但模板不含日期变量，二者必须一致");
        }
        if (!StringUtils.hasText(datePattern) && hasDateVar) {
            throw new BusinessException(400, "模板含日期变量但未设置流水号重置维度（datePattern）");
        }
    }

    private void applyDto(CodeRule rule, CodeRuleDTO dto) {
        rule.setRuleCode(dto.getRuleCode());
        rule.setRuleName(dto.getRuleName());
        rule.setPattern(dto.getPattern());
        rule.setDatePattern(StringUtils.hasText(dto.getDatePattern()) ? dto.getDatePattern() : null);
        rule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        rule.setRemark(dto.getRemark());
    }
}
