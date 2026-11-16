package com.dept.mgmt.tender.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 招标商务平台配置（AGT-27 标讯联网采集，R1 平台管理）
 *
 * <p>认证参数（authParamsCipher）AES-256-GCM 加密落库，接口层永不回显明文；
 * 采集规则（collectRules）为 JSON 配置：
 * <pre>{ "mode": "HTML|JSON_API",
 *        "list": { "itemSelector","titleSelector","linkSelector","dateSelector",
 *                  "itemsPath","titleField","linkField","pageParam","firstPage","nextSelector" },
 *        "detail": { "contentSelector","contentField" },
 *        "login": { "usernameField","passwordField","captchaField","captchaImgSrc",
 *                   "successKeyword","failKeyword" },
 *        "userAgent": "..." }</pre>
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tender_platform")
public class TenderPlatform extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 平台名称（唯一） */
    private String platformName;

    /** 登录入口 URL（NONE 模式可空） */
    private String loginUrl;

    /** 标讯列表页 URL（支持 {page} 占位符） */
    private String listUrl;

    /** 详情页 URL 前缀（列表项为相对链接时拼接，空则直接使用列表项链接） */
    private String detailUrlPrefix;

    /** 认证方式（NONE/FORM/COOKIE） */
    private String authType;

    /** 认证参数密文（AES-256-GCM，JSON：username/password/cookie；接口层永不回显明文） */
    private String authParamsCipher;

    /** 采集规则配置 JSON */
    private String collectRules;

    /** 请求最小间隔毫秒（限频） */
    private Integer requestIntervalMs;

    /** 随机抖动毫秒上限（拟人化反爬） */
    private Integer jitterMs;
 
    /** 单次采集最大列表页数 */
    private Integer maxPages;

    /** 是否启用（0停用 1启用） */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 运行时扩展（非表字段）：authConfigured 凭据是否已配置标识 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.Map<String, Object> ext = new java.util.LinkedHashMap<>();
}
