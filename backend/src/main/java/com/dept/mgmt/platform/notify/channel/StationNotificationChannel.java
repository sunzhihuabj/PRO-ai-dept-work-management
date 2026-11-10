package com.dept.mgmt.platform.notify.channel;

import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站内信通道（本期唯一实现）
 * 直接落 user_message 表，现有前端消息中心零改动可见
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StationNotificationChannel implements NotificationChannel {

    private final UserMessageMapper userMessageMapper;

    @Override
    public String channelCode() {
        return "STATION";
    }

    @Override
    public void send(List<UserMessage> messages) {
        for (UserMessage message : messages) {
            userMessageMapper.insert(message);
        }
        log.info("站内信发送完成: {} 条", messages.size());
    }
}
