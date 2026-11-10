package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMessageService {

    private final UserMessageMapper userMessageMapper;

    public PageResult<UserMessage> myMessages(Integer pageNum, Integer pageSize, Long userId, Integer isRead) {
        Page<UserMessage> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId);
        wrapper.eq(isRead != null, UserMessage::getIsRead, isRead);
        wrapper.orderByDesc(UserMessage::getCreateTime);
        userMessageMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    public long unreadCount(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId);
        wrapper.eq(UserMessage::getIsRead, 0);
        return userMessageMapper.selectCount(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, Long userId) {
        UserMessage msg = userMessageMapper.selectById(id);
        if (msg == null || !userId.equals(msg.getUserId())) {
            throw new BusinessException(404, "消息不存在");
        }
        if (msg.getIsRead() == null || msg.getIsRead() == 0) {
            msg.setIsRead(1);
            msg.setReadTime(LocalDateTime.now());
            userMessageMapper.updateById(msg);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId);
        wrapper.eq(UserMessage::getIsRead, 0);
        List<UserMessage> unread = userMessageMapper.selectList(wrapper);
        for (UserMessage msg : unread) {
            msg.setIsRead(1);
            msg.setReadTime(LocalDateTime.now());
            userMessageMapper.updateById(msg);
        }
        return unread.size();
    }
}
