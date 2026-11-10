package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysNotice;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.SysNoticeMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.mapper.UserMessageMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知公告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserMapper sysUserMapper;
    private final UserMessageMapper userMessageMapper;

    public List<SysNotice> list(Integer noticeType, Integer status) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(noticeType != null, SysNotice::getNoticeType, noticeType);
        wrapper.eq(status != null, SysNotice::getStatus, status);
        wrapper.orderByDesc(SysNotice::getCreateTime);
        return noticeMapper.selectList(wrapper);
    }

    /** 已发布公告列表（全员可见，供消息中心/工作台展示） */
    public List<SysNotice> listPublished() {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getStatus, 1);
        wrapper.isNotNull(SysNotice::getPublishTime);
        wrapper.orderByDesc(SysNotice::getPublishTime);
        return noticeMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysNotice dto) {
        if (!StringUtils.hasText(dto.getNoticeTitle())) {
            throw new BusinessException(400, "通知标题不能为空");
        }
        dto.setId(null);
        dto.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        noticeMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysNotice dto) {
        SysNotice exists = get(id);
        exists.setNoticeTitle(dto.getNoticeTitle());
        exists.setNoticeType(dto.getNoticeType());
        exists.setNoticeContent(dto.getNoticeContent());
        noticeMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        noticeMapper.deleteById(id);
    }

    /**
     * 发布公告：置正常状态并给全部启用用户写站内消息
     * @return 触达人数
     */
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long id) {
        SysNotice notice = get(id);
        LoginUser loginUser = LoginUser.current();
        notice.setStatus(1);
        notice.setPublishBy(loginUser != null ? loginUser.getUsername() : "system");
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getStatus, 1);
        List<SysUser> users = sysUserMapper.selectList(userWrapper);
        String type = notice.getNoticeType() != null && notice.getNoticeType() == 2 ? "公告" : "通知";
        for (SysUser user : users) {
            UserMessage msg = new UserMessage();
            msg.setUserId(user.getId());
            msg.setMessageType(type);
            msg.setTitle("【" + type + "】" + notice.getNoticeTitle());
            msg.setContent(notice.getNoticeContent());
            userMessageMapper.insert(msg);
        }
        log.info("公告发布: id={}, title={}, 触达{}人", id, notice.getNoticeTitle(), users.size());
        return users.size();
    }

    private SysNotice get(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "通知公告不存在");
        }
        return notice;
    }
}
