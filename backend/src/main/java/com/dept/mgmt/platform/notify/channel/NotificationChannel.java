package com.dept.mgmt.platform.notify.channel;

import com.dept.mgmt.entity.UserMessage;

import java.util.List;

/**
 * 通知通道扩展接口
 * 新通道（AGENT Agent 推送 / DINGTALK 钉钉 Webhook）实现本接口并注册为 Spring Bean 即可接入，
 * NotificationService 按 channelCode 路由，业务代码无感知
 *
 * @author dept-mgmt
 */
public interface NotificationChannel {

    /** 通道编码（唯一）：STATION / AGENT / DINGTALK ... */
    String channelCode();

    /**
     * 发送一批已渲染完成的通知消息
     *
     * @param messages 通知消息（含接收人/标题/内容/业务关联ID）
     */
    void send(List<UserMessage> messages);
}
