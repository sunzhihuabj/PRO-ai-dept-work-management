package com.dept.mgmt.tender.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 标讯采集运行监控（AGT-27，R3/R6：全链路指标 + 断点续采检查点 + 异常现场快照）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tender_collect_run")
public class TenderCollectRun extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 平台 ID */
    private Long platformId;

    /** 平台名称快照 */
    private String platformName;

    /** 触发方式（AGENT/AGENT_DIALOG/MANUAL） */
    private String triggerType;

    /** 触发用户 */
    private Long triggerUserId;

    /** 状态（RUNNING/SUCCESS/PARTIAL/FAILED） */
    private String status;

    /** 登录结果（NONE/FORM_OK/COOKIE_OK/FAILED） */
    private String loginStatus;

    /** 已抓取列表页数 */
    private Integer pagesFetched;

    /** 发现标讯条数 */
    private Integer itemsFound;

    /** 详情拉取成功条数 */
    private Integer itemsCollected;

    /** 提取校验全过条数 */
    private Integer extractOk;

    /** 二次核验补全条数 */
    private Integer extractRepaired;

    /** 提取失败（转人工）条数 */
    private Integer extractFailed;

    /** 登记成功条数 */
    private Integer registerOk;

    /** 去重跳过条数 */
    private Integer registerDup;

    /** 断点续采检查点 JSON（lastPage/doneUrls） */
    private String checkpoint;

    /** 最近异常类型（LOGIN_FAILED/NETWORK/EXTRACT/REGISTER） */
    private String lastErrorType;

    /** 异常现场快照（URL+状态码+响应摘要） */
    private String errorSnapshot;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;
}
