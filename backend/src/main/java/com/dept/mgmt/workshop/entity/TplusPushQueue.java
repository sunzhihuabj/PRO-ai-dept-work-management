package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * T+ 推送队列实体（T-204）
 * 出入库单推送 T+ 单向队列；失败留痕可重试；本机无 T+ 系统时推送 = 文件适配器落盘
 *
 * @author dept-mgmt
 */
@Data
@TableName("tplus_push_queue")
public class TplusPushQueue implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 队列ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出入库记录ID */
    private Long recordId;

    /** 出入库单号（冗余） */
    private String recordNo;

    /** T+推送报文 JSON */
    private String payloadJson;

    /** 状态（PENDING/PUSHED/FAILED） */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 推送成功时间 */
    private LocalDateTime pushedAt;

    /** 失败原因 */
    private String errorMsg;

    /** 入队时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
