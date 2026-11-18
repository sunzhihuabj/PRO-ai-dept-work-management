package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.SkillTemplate;
import com.dept.mgmt.mapper.SkillTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 任务书模板沉淀服务（V2.0 M8 步骤 3）：高频成功委托指令自动模板化，
 * 模板带使用统计与成功率；独立成服务避免 HermesDelegate → SelfIteration → AiChat → HermesDelegate 循环依赖。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillTemplateService {

    private final SkillTemplateMapper skillTemplateMapper;

    /** 模板名取指令前缀（24 字符内归一化空白） */
    private static final int NAME_LEN = 24;

    /**
     * 委托结果回写时调用：成功/失败计数沉淀（指令前缀作模板名，同名聚合使用统计）。
     */
    public void recordUsage(String instruction, boolean success) {
        if (!StringUtils.hasText(instruction)) {
            return;
        }
        try {
            String name = StringUtils.trimTrailingWhitespace(
                    instruction.replaceAll("\\s+", " ")).toLowerCase();
            name = name.length() > NAME_LEN ? name.substring(0, NAME_LEN) : name;
            SkillTemplate t = skillTemplateMapper.selectOne(new LambdaQueryWrapper<SkillTemplate>()
                    .eq(SkillTemplate::getTemplateName, name));
            if (t == null) {
                t = new SkillTemplate();
                t.setTemplateName(name);
                t.setTemplateContent(instruction.length() > 2000
                        ? instruction.substring(0, 2000) : instruction);
                t.setUseCount(1);
                t.setSuccessCount(success ? 1 : 0);
                t.setSuccessRate(BigDecimal.valueOf(success ? 1 : 0));
                t.setLastUsedAt(LocalDateTime.now());
                skillTemplateMapper.insert(t);
            } else {
                t.setUseCount(t.getUseCount() + 1);
                if (success) {
                    t.setSuccessCount(t.getSuccessCount() + 1);
                }
                t.setSuccessRate(BigDecimal.valueOf(t.getSuccessCount())
                        .divide(BigDecimal.valueOf(t.getUseCount()), 3, RoundingMode.HALF_UP));
                t.setLastUsedAt(LocalDateTime.now());
                skillTemplateMapper.updateById(t);
            }
        } catch (Exception e) {
            // 沉淀失败不阻塞委托主流程
            log.debug("Skill 模板沉淀失败（忽略）: {}", e.getMessage());
        }
    }

    /** 模板清单（按成功率/使用次数排序） */
    public java.util.List<SkillTemplate> list() {
        return skillTemplateMapper.selectList(new LambdaQueryWrapper<SkillTemplate>()
                .orderByDesc(SkillTemplate::getSuccessRate)
                .orderByDesc(SkillTemplate::getUseCount)
                .last("LIMIT 50"));
    }
}
