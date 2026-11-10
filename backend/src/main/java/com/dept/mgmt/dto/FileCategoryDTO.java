package com.dept.mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件分类 DTO
 */
@Data
public class FileCategoryDTO {

    /** 父分类ID（0 为顶级） */
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称最长100字符")
    private String categoryName;

    @Size(max = 50, message = "分类编码最长50字符")
    private String categoryCode;

    private Integer orderNum;

    private Integer status;
}
