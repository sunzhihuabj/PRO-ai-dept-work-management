package com.dept.mgmt.design.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.design.entity.DesignBomCheckLog;
import com.dept.mgmt.design.entity.DesignBomItem;
import com.dept.mgmt.design.entity.DesignScheme;
import com.dept.mgmt.design.entity.DesignSurvey;
import com.dept.mgmt.design.entity.DesignTemplate;
import com.dept.mgmt.event.bus.EventBusService;
import com.dept.mgmt.event.model.DomainEventType;
import com.dept.mgmt.mapper.DesignBomCheckLogMapper;
import com.dept.mgmt.mapper.DesignBomItemMapper;
import com.dept.mgmt.mapper.DesignSchemeMapper;
import com.dept.mgmt.mapper.DesignSurveyMapper;
import com.dept.mgmt.mapper.DesignTemplateMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * W3 设计域服务（T-115/T-116）
 * 方案版本管理 + 受控发布（发布须人工执行：发布是外发语义，AI 仅起草）
 * 受控发布时发布 design.released 领域事件（联动通知/档案归集）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignService {

    private final DesignSchemeMapper schemeMapper;
    private final DesignTemplateMapper templateMapper;
    private final DesignSurveyMapper surveyMapper;
    private final DesignBomItemMapper bomItemMapper;
    private final DesignBomCheckLogMapper checkLogMapper;
    private final BomCheckRules bomCheckRules;
    private final EventBusService eventBusService;

    // ===== 模板库 =====

    public List<DesignTemplate> listTemplates(String scene) {
        return templateMapper.selectList(new LambdaQueryWrapper<DesignTemplate>()
                .eq(DesignTemplate::getEnabled, 1)
                .like(StringUtils.hasText(scene), DesignTemplate::getScene, scene)
                .orderByAsc(DesignTemplate::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createTemplate(DesignTemplate template) {
        template.setEnabled(template.getEnabled() == null ? 1 : template.getEnabled());
        templateMapper.insert(template);
        return template.getId();
    }

    /** 场景匹配模板（AGT-09 调用）：场景精确 → 名称关键词 → 默认首个 */
    public DesignTemplate matchTemplate(String scene) {
        if (StringUtils.hasText(scene)) {
            DesignTemplate exact = templateMapper.selectOne(new LambdaQueryWrapper<DesignTemplate>()
                    .eq(DesignTemplate::getEnabled, 1).eq(DesignTemplate::getScene, scene).last("LIMIT 1"));
            if (exact != null) {
                return exact;
            }
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<DesignTemplate>()
                .eq(DesignTemplate::getEnabled, 1).last("LIMIT 1"));
    }

    // ===== 勘测记录 =====

    public List<DesignSurvey> listSurveys(Long projectId) {
        return surveyMapper.selectList(new LambdaQueryWrapper<DesignSurvey>()
                .eq(DesignSurvey::getProjectId, projectId).orderByDesc(DesignSurvey::getSurveyDate));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createSurvey(DesignSurvey survey) {
        surveyMapper.insert(survey);
        return survey.getId();
    }

    // ===== 方案版本 =====

    public PageResult<DesignScheme> listSchemes(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<DesignScheme> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, DesignScheme::getProjectId, projectId);
        wrapper.eq(StringUtils.hasText(status), DesignScheme::getStatus, status);
        wrapper.orderByDesc(DesignScheme::getId);
        return PageResult.of(schemeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public Map<String, Object> schemeDetail(Long id) {
        DesignScheme scheme = mustExistScheme(id);
        List<DesignBomItem> bom = bomItemMapper.selectList(new LambdaQueryWrapper<DesignBomItem>()
                .eq(DesignBomItem::getSchemeId, id).orderByAsc(DesignBomItem::getId));
        List<DesignBomCheckLog> logs = checkLogMapper.selectList(new LambdaQueryWrapper<DesignBomCheckLog>()
                .eq(DesignBomCheckLog::getSchemeId, id).orderByDesc(DesignBomCheckLog::getId));
        return Map.of("scheme", scheme, "bom", bom, "checkLogs", logs);
    }

    /**
     * 新建方案版本（人工/处理器共用；版本号项目内递增）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createScheme(Long projectId, Long contractId, String title, String content, String genType) {
        Integer maxVersion = schemeMapper.selectList(new LambdaQueryWrapper<DesignScheme>()
                        .eq(DesignScheme::getProjectId, projectId))
                .stream().map(DesignScheme::getVersionNo).max(Integer::compareTo).orElse(0);
        DesignScheme scheme = new DesignScheme();
        scheme.setProjectId(projectId);
        scheme.setContractId(contractId);
        scheme.setVersionNo(maxVersion + 1);
        scheme.setTitle(title);
        scheme.setContent(content);
        scheme.setGenType(StringUtils.hasText(genType) ? genType : "MANUAL");
        scheme.setStatus("DRAFT");
        schemeMapper.insert(scheme);
        log.info("方案版本创建: projectId={} v{} ({})", projectId, scheme.getVersionNo(), scheme.getGenType());
        return scheme.getId();
    }

    /**
     * 受控发布（DRAFT → RELEASED，人工执行；发布后不可修改）
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, String releaseNote) {
        DesignScheme scheme = mustExistScheme(id);
        if (!"DRAFT".equals(scheme.getStatus())) {
            throw new BusinessException(400, "仅草稿状态可发布，当前: " + scheme.getStatus());
        }
        scheme.setStatus("RELEASED");
        scheme.setReleaseNote(releaseNote);
        scheme.setReleaseTime(LocalDateTime.now());
        LoginUser user = LoginUser.current();
        scheme.setReleasedBy(user == null ? "系统" : user.getUsername());
        schemeMapper.updateById(scheme);
        // 受控发布跨域事件（T-108 语义：设计域 → 通知/档案域）
        eventBusService.publish(DomainEventType.DESIGN_RELEASED, Map.of(
                "schemeId", scheme.getId(),
                "projectId", scheme.getProjectId(),
                "versionNo", scheme.getVersionNo(),
                "title", scheme.getTitle()));
        log.info("方案受控发布: schemeId={} v{}", scheme.getId(), scheme.getVersionNo());
    }

    // ===== BOM =====

    /**
     * 覆盖式录入 BOM 明细（草稿方案可重建；发布后禁止）
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceBom(Long schemeId, List<DesignBomItem> items) {
        DesignScheme scheme = mustExistScheme(schemeId);
        if (!"DRAFT".equals(scheme.getStatus())) {
            throw new BusinessException(400, "仅草稿方案可编辑 BOM，当前: " + scheme.getStatus());
        }
        bomItemMapper.delete(new LambdaQueryWrapper<DesignBomItem>().eq(DesignBomItem::getSchemeId, schemeId));
        for (DesignBomItem item : items) {
            item.setId(null);
            item.setSchemeId(schemeId);
            bomItemMapper.insert(item);
        }
    }

    /**
     * 执行 BOM 校验并留痕（AGT-11 处理器与人工入口共用）
     */
    @Transactional(rollbackFor = Exception.class)
    public BomCheckRules.CheckResult checkBom(Long schemeId, String checker) {
        mustExistScheme(schemeId);
        List<DesignBomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<DesignBomItem>()
                .eq(DesignBomItem::getSchemeId, schemeId).orderByAsc(DesignBomItem::getId));
        BomCheckRules.CheckResult result = bomCheckRules.check(items);
        DesignBomCheckLog logEntry = new DesignBomCheckLog();
        logEntry.setSchemeId(schemeId);
        logEntry.setPassed(result.passed() ? 1 : 0);
        logEntry.setTotalItems(result.totalItems());
        try {
            logEntry.setResultJson(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of("violations", result.violations())));
        } catch (Exception e) {
            logEntry.setResultJson("[]");
        }
        logEntry.setChecker(StringUtils.hasText(checker) ? checker : "MANUAL");
        checkLogMapper.insert(logEntry);
        return result;
    }

    public List<DesignBomCheckLog> listCheckLogs(Long schemeId) {
        return checkLogMapper.selectList(new LambdaQueryWrapper<DesignBomCheckLog>()
                .eq(DesignBomCheckLog::getSchemeId, schemeId).orderByDesc(DesignBomCheckLog::getId));
    }

    /**
     * 项目上下文快照（AGT-09 起草输入：项目名/客户/勘测结论/模板）
     */
    public Map<String, Object> projectContext(Long projectId) {
        Map<String, Object> ctx = new HashMap<>();
        List<DesignSurvey> surveys = listSurveys(projectId);
        StringBuilder surveyText = new StringBuilder();
        for (DesignSurvey s : surveys) {
            surveyText.append("点位 ").append(s.getSiteName())
                    .append("（风区 ").append(s.getWindClass() == null ? "-" : s.getWindClass())
                    .append("）：").append(s.getContent() == null ? "" : s.getContent()).append("\n");
        }
        ctx.put("surveyText", surveyText.toString());
        return ctx;
    }

    private DesignScheme mustExistScheme(Long id) {
        DesignScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new BusinessException(404, "方案不存在: " + id);
        }
        return scheme;
    }
}
