package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件分类表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_category")
public class FileCategory extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private Integer orderNum;
    private Integer status;
}
