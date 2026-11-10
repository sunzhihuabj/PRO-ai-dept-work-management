package com.dept.mgmt.dto;

import lombok.Data;

/**
 * 文件审核请求 DTO
 */
@Data
public class FileAuditDTO {
    /** 审核状态：APPROVED / REJECTED */
    private String status;
    /** 审核意见 */
    private String comment;
}
