package com.dept.mgmt.fineperm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 权限模板请求 DTO（T-404，创建/更新复用）
 *
 * @author dept-mgmt
 */
@Data
public class TemplateDTO {

    /** 模板编码（唯一） */
    @NotBlank
    private String templateCode;

    /** 模板名称 */
    @NotBlank
    private String templateName;

    /** 描述 */
    private String description;

    /** 模板条目（更新时全量替换） */
    private List<Item> items;

    /**
     * 模板条目
     */
    @Data
    public static class Item {

        /** 权限点编码 */
        private String permCode;

        /** 权限类型（MENU/BUTTON/API） */
        private String permType;

        /** 效果（GRANT/DENY） */
        private String effect;
    }
}
