package com.dept.mgmt.fineperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.fineperm.dto.TemplateDTO;
import com.dept.mgmt.fineperm.entity.SysPermTemplate;
import com.dept.mgmt.fineperm.entity.SysPermTemplateItem;
import com.dept.mgmt.mapper.SysPermTemplateItemMapper;
import com.dept.mgmt.mapper.SysPermTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限模板服务（T-404）
 * 模板 CRUD + 条目全量维护；模板应用走 FinePermAssignmentService.applyTemplate
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePermTemplateService {

    private final SysPermTemplateMapper templateMapper;
    private final SysPermTemplateItemMapper itemMapper;

    /**
     * 模板列表（含条目）
     */
    public List<Map<String, Object>> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<SysPermTemplate>()
                .orderByAsc(SysPermTemplate::getId)).stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("templateCode", t.getTemplateCode());
            m.put("templateName", t.getTemplateName());
            m.put("description", t.getDescription());
            m.put("createTime", t.getCreateTime());
            m.put("items", listItems(t.getId()));
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 创建模板
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createTemplate(TemplateDTO dto) {
        Long count = templateMapper.selectCount(new LambdaQueryWrapper<SysPermTemplate>()
                .eq(SysPermTemplate::getTemplateCode, dto.getTemplateCode()));
        if (count != null && count > 0) {
            throw new BusinessException(400, "模板编码已存在: " + dto.getTemplateCode());
        }
        SysPermTemplate template = new SysPermTemplate();
        template.setTemplateCode(dto.getTemplateCode());
        template.setTemplateName(dto.getTemplateName());
        template.setDescription(dto.getDescription());
        templateMapper.insert(template);
        saveItems(template.getId(), dto.getItems());
        return template.getId();
    }

    /**
     * 更新模板（条目全量替换）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long id, TemplateDTO dto) {
        SysPermTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "模板不存在: " + id);
        }
        if (dto.getTemplateName() != null) {
            template.setTemplateName(dto.getTemplateName());
        }
        if (dto.getDescription() != null) {
            template.setDescription(dto.getDescription());
        }
        templateMapper.updateById(template);
        if (dto.getItems() != null) {
            itemMapper.delete(new LambdaQueryWrapper<SysPermTemplateItem>()
                    .eq(SysPermTemplateItem::getTemplateId, id));
            saveItems(id, dto.getItems());
        }
    }

    /**
     * 删除模板（含条目）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<SysPermTemplateItem>()
                .eq(SysPermTemplateItem::getTemplateId, id));
    }

    /**
     * 模板条目列表
     */
    public List<SysPermTemplateItem> listItems(Long templateId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SysPermTemplateItem>()
                .eq(SysPermTemplateItem::getTemplateId, templateId)
                .orderByAsc(SysPermTemplateItem::getSortOrder));
    }

    private void saveItems(Long templateId, List<TemplateDTO.Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int sort = 1;
        for (TemplateDTO.Item item : items) {
            if (item.getPermCode() == null || item.getPermCode().isBlank()) {
                continue;
            }
            SysPermTemplateItem entity = new SysPermTemplateItem();
            entity.setTemplateId(templateId);
            entity.setPermCode(item.getPermCode());
            entity.setPermType(item.getPermType() == null ? inferType(item.getPermCode()) : item.getPermType());
            entity.setEffect(item.getEffect() == null ? "GRANT" : item.getEffect());
            entity.setSortOrder(sort++);
            itemMapper.insert(entity);
        }
    }

    private String inferType(String permCode) {
        if (permCode.startsWith("api:")) {
            return "API";
        }
        if (permCode.startsWith("btn:")) {
            return "BUTTON";
        }
        return "MENU";
    }
}
