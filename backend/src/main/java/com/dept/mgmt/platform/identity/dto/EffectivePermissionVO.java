package com.dept.mgmt.platform.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前用户有效权限视图
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class EffectivePermissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户身份编码（EXPERT/WORKER） */
    private String identity;

    /** 身份中文名 */
    private String identityLabel;

    /** 有效能力点列表（仅 is_never 拦截 Agent，不影响人类，全量返回） */
    private List<CapabilityVO> capabilities;

    @Data
    @AllArgsConstructor
    public static class CapabilityVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String capabilityCode;
        private String capabilityName;
        private String category;
        private Integer isNever;
    }
}
