package com.dept.mgmt.tender.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台登录会话（AGT-27，R2 自动登录/会话复用）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tender_platform_session")
public class TenderPlatformSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 平台 ID（唯一） */
    private Long platformId;

    /** 会话 Cookie 密文（AES-256-GCM） */
    private String cookiesCipher;

    /** 本次登录方式（FORM/COOKIE） */
    private String loginType;

    /** 状态（ACTIVE/EXPIRED/BLOCKED） */
    private String status;

    /** 最近登录/校验时间 */
    private LocalDateTime lastLoginAt;

    /** 预计过期时间（可空） */
    private LocalDateTime expiresAt;

    /** 连续校验失败次数（>=3 标记 BLOCKED 并告警） */
    private Integer failCount;

    /** 备注（如验证码识别方式） */
    private String remark;
}
