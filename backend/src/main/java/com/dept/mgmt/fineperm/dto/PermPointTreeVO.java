package com.dept.mgmt.fineperm.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限点树节点 VO（T-404）
 * MENU 节点下挂 BUTTON/API 子节点，配置界面按树渲染
 *
 * @author dept-mgmt
 */
@Data
public class PermPointTreeVO {

    /** 权限点编码 */
    private String permCode;

    /** 权限点名称 */
    private String permName;

    /** 权限类型（MENU/BUTTON/API） */
    private String permType;

    /** 子节点（BUTTON/API） */
    private List<PermPointTreeVO> children = new ArrayList<>();
}
