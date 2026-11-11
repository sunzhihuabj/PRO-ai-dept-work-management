package com.dept.mgmt.dingtalk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 钉钉企业应用配置（T-309 免登+审批回调，单行配置）
 * appSecret/auditSignKey AES-GCM 加密存储，接口永不回显明文
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_dingtalk_config")
public class DingTalkConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业 corpId */
    private String corpId;

    /** 应用 appKey（Client ID） */
    private String appKey;

    /** 应用 appSecret（Client Secret，AES-GCM 加密存储） */
    @JsonIgnore
    private String appSecretEnc;

    /** 应用 agentId（工作通知用） */
    private String agentId;

    /** 审批回调验签密钥（AES-GCM 加密存储；未配置时审批回调拒绝） */
    @JsonIgnore
    private String auditSignKeyEnc;

    /** 状态（1启用/0停用） */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
