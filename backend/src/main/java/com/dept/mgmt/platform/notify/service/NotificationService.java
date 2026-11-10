package com.dept.mgmt.platform.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.NotificationTemplateMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.platform.notify.channel.NotificationChannel;
import com.dept.mgmt.platform.notify.dto.NotificationSendDTO;
import com.dept.mgmt.platform.notify.dto.NotificationSendResult;
import com.dept.mgmt.platform.notify.entity.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通知中心统一出口
 * 模板渲染 → 接收人过滤（禁用跳过不阻塞）→ 通道分发（本期站内信，预留扩展）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
public class NotificationService {

    /** 模板占位符语法：{param} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /** 默认通道：站内信 */
    private static final String DEFAULT_CHANNEL = "STATION";

    private final NotificationTemplateMapper templateMapper;
    private final SysUserMapper sysUserMapper;
    private final Map<String, NotificationChannel> channels;

    /**
     * @param templateMapper 模板 Mapper
     * @param sysUserMapper  用户 Mapper（接收人有效性过滤）
     * @param channelBeans   所有通知通道实现（Spring 自动注入）
     */
    public NotificationService(NotificationTemplateMapper templateMapper,
                               SysUserMapper sysUserMapper,
                               List<NotificationChannel> channelBeans) {
        this.templateMapper = templateMapper;
        this.sysUserMapper = sysUserMapper;
        this.channels = channelBeans.stream()
                .collect(Collectors.toMap(NotificationChannel::channelCode, c -> c));
    }

    /**
     * 发送通知
     *
     * @return 送达/跳过统计
     */
    public NotificationSendResult send(NotificationSendDTO dto) {
        // 1. 模板校验（必须启用）
        NotificationTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<NotificationTemplate>()
                .eq(NotificationTemplate::getTemplateCode, dto.getTemplateCode())
                .eq(NotificationTemplate::getStatus, 1));
        if (template == null) {
            throw new BusinessException(404, "通知模板不存在或已停用: " + dto.getTemplateCode());
        }

        // 2. 模板渲染（缺参数保留原文并告警，不阻塞通知）
        String title = render(template.getTitleTemplate(), dto.getParams());
        String content = template.getContentTemplate() == null ? "" : render(template.getContentTemplate(), dto.getParams());

        // 3. 接收人过滤：仅启用用户，无效跳过
        List<Long> receiverIds = dto.getReceiverIds();
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, receiverIds)
                .eq(SysUser::getStatus, 1));
        int skipped = receiverIds.size() - users.size();
        if (skipped > 0) {
            log.warn("通知接收人含无效/禁用账号已跳过: template={}, skipped={}", dto.getTemplateCode(), skipped);
        }

        // 4. 组装消息
        List<UserMessage> messages = new ArrayList<>(users.size());
        for (SysUser user : users) {
            UserMessage msg = new UserMessage();
            msg.setUserId(user.getId());
            msg.setMessageType(StringUtils.hasText(template.getMessageType()) ? template.getMessageType() : "系统通知");
            msg.setTitle(title);
            msg.setContent(content);
            msg.setRelatedId(dto.getRelatedId());
            msg.setIsRead(0);
            messages.add(msg);
        }

        // 5. 通道分发
        String channelCode = StringUtils.hasText(dto.getChannel()) ? dto.getChannel() : DEFAULT_CHANNEL;
        NotificationChannel channel = channels.get(channelCode);
        if (channel == null) {
            throw new BusinessException(400, "通知通道不存在: " + channelCode + "，可用通道: " + channels.keySet());
        }
        if (!messages.isEmpty()) {
            channel.send(messages);
        }
        return new NotificationSendResult(messages.size(), skipped, channelCode);
    }

    /**
     * 宽松渲染：缺失参数保留 {param} 原文并告警
     */
    private String render(String templateText, Map<String, String> params) {
        Matcher m = PLACEHOLDER.matcher(templateText);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = params == null ? null : params.get(name);
            if (value == null) {
                log.warn("通知模板参数缺失，保留占位符: param={}", name);
                value = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
