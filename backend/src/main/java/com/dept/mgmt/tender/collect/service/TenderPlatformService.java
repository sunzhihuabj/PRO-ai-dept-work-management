package com.dept.mgmt.tender.collect.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dingtalk.service.CryptoService;
import com.dept.mgmt.mapper.TenderPlatformMapper;
import com.dept.mgmt.tender.collect.entity.TenderPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招标商务平台管理服务（AGT-27，R1 平台管理模块）
 *
 * <p>人工动态添加/编辑/删除/启停平台配置；认证参数加密落库、查询结果永不回显明文
 * （仅返回 authConfigured 布尔标识）；采集规则 JSON 结构合法性校验。
 * 写操作权限由控制器层 @PreAuthorize(ROLE_DEPT_LEADER) 管控。
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderPlatformService {

    private static final List<String> AUTH_TYPES = List.of("NONE", "FORM", "COOKIE");

    private final TenderPlatformMapper platformMapper;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    /** 分页查询（凭据脱敏：仅返回 authConfigured 标识） */
    public Page<TenderPlatform> page(int pageNum, int pageSize, String keyword) {
        Page<TenderPlatform> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TenderPlatform> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(TenderPlatform::getPlatformName, keyword);
        }
        wrapper.orderByDesc(TenderPlatform::getCreateTime);
        platformMapper.selectPage(page, wrapper);
        page.getRecords().forEach(TenderPlatformService::mask);
        return page;
    }

    public List<TenderPlatform> listEnabled() {
        return platformMapper.selectList(new LambdaQueryWrapper<TenderPlatform>()
                .eq(TenderPlatform::getEnabled, 1)
                .orderByAsc(TenderPlatform::getId));
    }

    public TenderPlatform detail(Long id) {
        TenderPlatform platform = mustExist(id);
        mask(platform);
        return platform;
    }

    /**
     * 新增平台。authParams 为明文入参（username/password/cookie），本方法内即时加密，
     * 明文不落日志不落库。
     */
    public Long create(String platformName, String loginUrl, String listUrl, String detailUrlPrefix,
                       String authType, Map<String, Object> authParams, String collectRules,
                       Integer requestIntervalMs, Integer jitterMs, Integer maxPages, String remark) {
        TenderPlatform platform = new TenderPlatform();
        applyCommon(platform, platformName, loginUrl, listUrl, detailUrlPrefix, authType,
                authParams, collectRules, requestIntervalMs, jitterMs, maxPages, remark);
        Long count = platformMapper.selectCount(new LambdaQueryWrapper<TenderPlatform>()
                .eq(TenderPlatform::getPlatformName, platformName));
        if (count != null && count > 0) {
            throw new BusinessException(400, "平台名称已存在: " + platformName);
        }
        platformMapper.insert(platform);
        log.info("采集平台已创建: id={} name={} authType={}", platform.getId(), platformName, authType);
        return platform.getId();
    }

    /** 编辑平台。authParams 传 null 表示不修改凭据（避免误清空） */
    public void update(Long id, String platformName, String loginUrl, String listUrl, String detailUrlPrefix,
                       String authType, Map<String, Object> authParams, String collectRules,
                       Integer requestIntervalMs, Integer jitterMs, Integer maxPages, String remark) {
        TenderPlatform platform = mustExist(id);
        applyCommon(platform, platformName, loginUrl, listUrl, detailUrlPrefix, authType,
                authParams, collectRules, requestIntervalMs, jitterMs, maxPages, remark);
        if (authParams == null) {
            // 未提交凭据字段 → 保留原密文
            platform.setAuthParamsCipher(mustExist(id).getAuthParamsCipher());
        }
        platformMapper.updateById(platform);
        log.info("采集平台已更新: id={}", id);
    }

    public void delete(Long id) {
        mustExist(id);
        // Wrapper 删除（逻辑删除字段由 MyBatis-Plus 全局配置接管）
        platformMapper.delete(new LambdaQueryWrapper<TenderPlatform>().eq(TenderPlatform::getId, id));
        log.info("采集平台已删除: id={}", id);
    }

    public void toggle(Long id, Integer enabled) {
        TenderPlatform platform = mustExist(id);
        platform.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        platformMapper.updateById(platform);
    }

    /** 采集规则 JSON 结构校验（mode + 必填路径存在性） */
    public void validateRules(String collectRules) {
        if (collectRules == null || collectRules.isBlank()) {
            throw new BusinessException(400, "采集规则配置不能为空");
        }
        Map<?, ?> rules;
        try {
            rules = objectMapper.readValue(collectRules, Map.class);
        } catch (Exception e) {
            throw new BusinessException(400, "采集规则不是合法 JSON: " + e.getMessage());
        }
        String mode = String.valueOf(rules.get("mode"));
        if (!List.of("HTML", "JSON_API").contains(mode)) {
            throw new BusinessException(400, "采集规则 mode 仅允许 HTML / JSON_API");
        }
        if (!(rules.get("list") instanceof Map<?, ?> list)) {
            throw new BusinessException(400, "采集规则缺少 list 配置");
        }
        if ("HTML".equals(mode)) {
            if (isBlank(list.get("itemSelector")) || isBlank(list.get("titleSelector"))) {
                throw new BusinessException(400, "HTML 模式必须配置 itemSelector 与 titleSelector");
            }
        } else if (isBlank(list.get("itemsPath")) || isBlank(list.get("titleField"))) {
            throw new BusinessException(400, "JSON_API 模式必须配置 itemsPath 与 titleField");
        }
    }

    public TenderPlatform mustExist(Long id) {
        TenderPlatform platform = platformMapper.selectById(id);
        if (platform == null) {
            throw new BusinessException(404, "采集平台不存在: " + id);
        }
        return platform;
    }

    private void applyCommon(TenderPlatform platform, String platformName, String loginUrl, String listUrl,
                             String detailUrlPrefix, String authType, Map<String, Object> authParams,
                             String collectRules, Integer requestIntervalMs, Integer jitterMs,
                             Integer maxPages, String remark) {
        if (platformName == null || platformName.isBlank()) {
            throw new BusinessException(400, "平台名称不能为空");
        }
        if (listUrl == null || listUrl.isBlank()) {
            throw new BusinessException(400, "标讯列表页 URL 不能为空");
        }
        String type = authType == null || authType.isBlank() ? "NONE" : authType.toUpperCase();
        if (!AUTH_TYPES.contains(type)) {
            throw new BusinessException(400, "认证方式仅允许 NONE/FORM/COOKIE");
        }
        if ("FORM".equals(type) && (loginUrl == null || loginUrl.isBlank())) {
            throw new BusinessException(400, "FORM 登录必须配置登录入口 URL");
        }
        validateRules(collectRules);
        platform.setPlatformName(platformName.trim());
        platform.setLoginUrl(blankToNull(loginUrl));
        platform.setListUrl(listUrl.trim());
        platform.setDetailUrlPrefix(blankToNull(detailUrlPrefix));
        platform.setAuthType(type);
        if (authParams != null) {
            // 即时加密：明文只存在于本次调用栈
            try {
                platform.setAuthParamsCipher(cryptoService.encrypt(objectMapper.writeValueAsString(authParams)));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(500, "凭据序列化失败");
            }
        }
        platform.setCollectRules(collectRules);
        platform.setRequestIntervalMs(requestIntervalMs == null || requestIntervalMs < 500 ? 1500 : requestIntervalMs);
        platform.setJitterMs(jitterMs == null || jitterMs < 0 ? 800 : jitterMs);
        platform.setMaxPages(maxPages == null || maxPages < 1 ? 3 : Math.min(maxPages, 50));
        platform.setRemark(remark);
        platform.setEnabled(platform.getEnabled() == null ? 1 : platform.getEnabled());
    }

    /** 脱敏：清空密文，补 authConfigured 标识（透传给前端用 transient 字段不序列化密文） */
    private static void mask(TenderPlatform platform) {
        boolean configured = platform.getAuthParamsCipher() != null && !platform.getAuthParamsCipher().isBlank();
        platform.setAuthParamsCipher(null);
        platform.getExt().put("authConfigured", configured);
    }

    private boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
