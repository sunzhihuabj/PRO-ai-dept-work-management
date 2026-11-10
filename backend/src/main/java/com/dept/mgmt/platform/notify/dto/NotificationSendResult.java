package com.dept.mgmt.platform.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 通知发送结果
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class NotificationSendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功送达人数 */
    private int sent;

    /** 跳过人数（接收人不存在或已禁用，不阻塞发送） */
    private int skipped;

    /** 实际使用的通道编码 */
    private String channel;
}
