package com.dept.mgmt.platform.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知模板实体
 * title/content 中 {param} 占位符由 NotificationService 渲染
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notification_template")
public class NotificationTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码（唯一） */
    private String templateCode;

    /** 标题模板 */
    private String titleTemplate;

    /** 内容模板 */
    private String contentTemplate;

    /** 落 user_message.message_type 的分类（系统通知/审核通知/任务提醒...） */
    private String messageType;

    /** 状态（1启用 0停用） */
    private Integer status;
}
