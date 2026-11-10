package com.dept.mgmt.platform.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 通知发送 DTO
 *
 * @author dept-mgmt
 */
@Data
public class NotificationSendDTO {

    /** 通知模板编码 */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /** 模板变量（{param} 占位符取值） */
    private Map<String, String> params;

    /** 接收用户ID列表 */
    @NotEmpty(message = "接收人不能为空")
    @Size(max = 500, message = "单次接收人不能超过500人")
    private List<Long> receiverIds;

    /** 关联业务ID（前端跳转用，可空） */
    private Long relatedId;

    /** 发送通道：STATION=站内信（默认），预留 AGENT/DINGTALK */
    private String channel;
}
