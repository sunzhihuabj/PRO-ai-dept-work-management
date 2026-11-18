package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.AiDocDraft;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mapper.AiDocDraftMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 文档工作台服务（V2.0 M6）：起草落库 / 版本递增 / 合规送审状态流转。
 *
 * <p>起草来源：Hermes 委托产出经 MCP doc.draft.create 落库（经审批）、
 * Agent 任务产出、工作台人工创建。每次编辑版本 +1（版本可回溯）。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocDraftService {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_REVIEW = "REVIEW";
    public static final String ST_APPROVED = "APPROVED";

    private final AiDocDraftMapper draftMapper;
    private final CodeEngineService codeEngineService;

    /** 创建草稿（version=1） */
    public AiDocDraft create(String title, String contentMd, String source, Long conversationId, String username) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(400, "文档标题不能为空");
        }
        AiDocDraft d = new AiDocDraft();
        d.setDocNo(codeEngineService.nextCode("DOC", null));
        d.setTitle(title.trim());
        d.setContentMd(contentMd == null ? "" : contentMd);
        d.setVersion(1);
        d.setStatus(ST_DRAFT);
        d.setSource(source == null ? "MANUAL" : source);
        d.setConversationId(conversationId);
        d.setCreateBy(username);
        d.setUpdateBy(username);
        draftMapper.insert(d);
        log.info("文档草稿已创建: docNo={}, title={}, source={}", d.getDocNo(), d.getTitle(), d.getSource());
        return d;
    }

    /** 编辑草稿（版本 +1，状态回 DRAFT） */
    public AiDocDraft update(Long id, String title, String contentMd) {
        AiDocDraft d = mustGet(id);
        LoginUser user = LoginUser.current();
        if (StringUtils.hasText(title)) {
            d.setTitle(title.trim());
        }
        if (contentMd != null) {
            d.setContentMd(contentMd);
        }
        d.setVersion(d.getVersion() + 1);
        d.setStatus(ST_DRAFT);
        d.setUpdateBy(user == null ? "system" : user.getUsername());
        draftMapper.updateById(d);
        return d;
    }

    /** 送审（DRAFT → REVIEW） */
    public AiDocDraft submitReview(Long id) {
        AiDocDraft d = mustGet(id);
        if (!ST_DRAFT.equals(d.getStatus())) {
            throw new BusinessException(400, "仅草稿状态可送审（当前 " + d.getStatus() + "）");
        }
        d.setStatus(ST_REVIEW);
        draftMapper.updateById(d);
        log.info("文档送审: docNo={}, version={}", d.getDocNo(), d.getVersion());
        return d;
    }

    /** 审核通过（REVIEW → APPROVED） */
    public AiDocDraft approve(Long id) {
        AiDocDraft d = mustGet(id);
        if (!ST_REVIEW.equals(d.getStatus())) {
            throw new BusinessException(400, "仅待审状态可通过审核（当前 " + d.getStatus() + "）");
        }
        d.setStatus(ST_APPROVED);
        draftMapper.updateById(d);
        return d;
    }

    /** 草稿列表（状态过滤，最新优先） */
    public List<AiDocDraft> list(String status, int limit) {
        LambdaQueryWrapper<AiDocDraft> qw = new LambdaQueryWrapper<AiDocDraft>()
                .orderByDesc(AiDocDraft::getUpdateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100));
        if (StringUtils.hasText(status)) {
            qw.eq(AiDocDraft::getStatus, status);
        }
        return draftMapper.selectList(qw);
    }

    public AiDocDraft get(Long id) {
        return mustGet(id);
    }

    private AiDocDraft mustGet(Long id) {
        AiDocDraft d = draftMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "文档不存在: " + id);
        }
        return d;
    }
}
