package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.dto.EventCreateDTO;
import com.dept.mgmt.dto.EventHandleDTO;
import com.dept.mgmt.entity.EventHandle;
import com.dept.mgmt.entity.EventInfo;
import com.dept.mgmt.mapper.EventHandleMapper;
import com.dept.mgmt.mapper.EventInfoMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件审批服务
 * 状态定义（与数据库注释一致）：
 * 0待审批 1处理中 2已完成 3已关闭 4已驳回
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_CLOSED = 3;
    public static final int STATUS_REJECTED = 4;

    private final EventInfoMapper eventInfoMapper;
    private final EventHandleMapper eventHandleMapper;

    /**
     * 事件列表分页
     */
    public PageResult<EventInfo> list(Integer pageNum, Integer pageSize, Long projectId, Integer eventType,
                                      Integer status, Integer urgency, Long handlerId, Long applicantId, String keyword) {
        Page<EventInfo> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<EventInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, EventInfo::getProjectId, projectId);
        wrapper.eq(eventType != null, EventInfo::getEventType, eventType);
        wrapper.eq(status != null, EventInfo::getStatus, status);
        wrapper.eq(urgency != null, EventInfo::getUrgency, urgency);
        wrapper.eq(handlerId != null, EventInfo::getHandlerId, handlerId);
        wrapper.eq(applicantId != null, EventInfo::getApplicantId, applicantId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(EventInfo::getEventTitle, keyword)
                    .or().like(EventInfo::getDescription, keyword));
        }
        wrapper.orderByAsc(EventInfo::getUrgency).orderByDesc(EventInfo::getCreateTime);
        eventInfoMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 事件详情
     */
    public EventInfo detail(Long id) {
        EventInfo event = eventInfoMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(404, "事件不存在");
        }
        return event;
    }

    /**
     * 创建事件
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(EventCreateDTO dto) {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        EventInfo event = new EventInfo();
        event.setProjectId(dto.getProjectId());
        event.setEventType(dto.getEventType());
        event.setEventTitle(dto.getEventTitle());
        event.setUrgency(dto.getUrgency() == null ? 2 : dto.getUrgency());
        event.setStatus(STATUS_PENDING);
        event.setApplicantId(loginUser.getUserId());
        event.setHandlerId(dto.getHandlerId());
        event.setDescription(dto.getDescription());
        eventInfoMapper.insert(event);

        recordHandle(event.getId(), "提交", "申请人提交事件", dto.getDescription());
        log.info("创建事件成功: id={}, title={}, applicant={}", event.getId(), event.getEventTitle(), loginUser.getUsername());
        return event.getId();
    }

    /**
     * 更新事件（仅限待审批或已驳回状态，且只能申请人修改）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EventCreateDTO dto) {
        EventInfo event = detail(id);
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null || !loginUser.getUserId().equals(event.getApplicantId())) {
            throw new BusinessException(403, "只有申请人可以修改事件");
        }
        if (event.getStatus() != STATUS_PENDING && event.getStatus() != STATUS_REJECTED) {
            throw new BusinessException(409, "仅待审批/已驳回的事件可修改");
        }
        event.setProjectId(dto.getProjectId());
        event.setEventType(dto.getEventType());
        event.setEventTitle(dto.getEventTitle());
        event.setUrgency(dto.getUrgency());
        event.setHandlerId(dto.getHandlerId());
        event.setDescription(dto.getDescription());
        // 修改后重新进入待审批
        event.setStatus(STATUS_PENDING);
        eventInfoMapper.updateById(event);
        recordHandle(id, "修改", "申请人修改并重新提交", dto.getDescription());
    }

    /**
     * 删除事件（待审批状态且申请人才可删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EventInfo event = detail(id);
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null || !loginUser.getUserId().equals(event.getApplicantId())) {
            throw new BusinessException(403, "只有申请人可以删除事件");
        }
        if (event.getStatus() != STATUS_PENDING && event.getStatus() != STATUS_REJECTED) {
            throw new BusinessException(409, "仅待审批/已驳回的事件可删除");
        }
        eventInfoMapper.deleteById(id);
        log.info("删除事件成功: id={}", id);
    }

    /**
     * 处理事件（受理/驳回/转派/处理/完成/关闭）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, EventHandleDTO dto) {
        EventInfo event = detail(id);
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        Long operatorId = loginUser.getUserId();

        // 处理权限校验：受理/驳回/转派/处理/完成/关闭 只能由指定处理人操作
        boolean isHandler = event.getHandlerId() != null && event.getHandlerId().equals(operatorId);
        switch (dto.getHandleType()) {
            case "受理" -> {
                if (!isHandler) throw new BusinessException(403, "只有指定处理人可以受理该事件");
                requireStatus(event, STATUS_PENDING);
                event.setStatus(STATUS_PROCESSING);
            }
            case "驳回" -> {
                if (!isHandler) throw new BusinessException(403, "只有指定处理人可以驳回该事件");
                requireStatus(event, STATUS_PENDING);
                event.setStatus(STATUS_REJECTED);
            }
            case "转派" -> {
                if (!isHandler) throw new BusinessException(403, "只有当前处理人可以转派该事件");
                requireStatus(event, STATUS_PENDING, STATUS_PROCESSING);
                if (dto.getTransferTo() == null) {
                    throw new BusinessException(400, "转派时必须指定新的处理人");
                }
                event.setHandlerId(dto.getTransferTo());
            }
            case "处理" -> {
                if (!isHandler) throw new BusinessException(403, "只有处理人可以登记处理记录");
                requireStatus(event, STATUS_PROCESSING);
            }
            case "完成" -> {
                if (!isHandler) throw new BusinessException(403, "只有处理人可以标记完成");
                requireStatus(event, STATUS_PROCESSING);
                event.setStatus(STATUS_DONE);
                event.setCloseTime(LocalDateTime.now());
            }
            case "关闭" -> {
                // 申请人或处理人都可关闭
                boolean isApplicant = event.getApplicantId() != null && event.getApplicantId().equals(operatorId);
                if (!isHandler && !isApplicant) {
                    throw new BusinessException(403, "只有申请人或处理人可以关闭事件");
                }
                requireStatus(event, STATUS_PENDING, STATUS_PROCESSING, STATUS_DONE, STATUS_REJECTED);
                event.setStatus(STATUS_CLOSED);
                if (event.getCloseTime() == null) {
                    event.setCloseTime(LocalDateTime.now());
                }
            }
            default -> throw new BusinessException(400, "不支持的处理类型: " + dto.getHandleType());
        }

        eventInfoMapper.updateById(event);
        recordHandle(id, dto.getHandleType(), dto.getHandleDesc(), dto.getHandleResult());
        log.info("事件处理完成: eventId={}, type={}, operator={}", id, dto.getHandleType(), operatorId);
    }

    /**
     * 查询事件的处理记录，按时间正序
     */
    public List<EventHandle> listHandles(Long eventId) {
        LambdaQueryWrapper<EventHandle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventHandle::getEventId, eventId);
        wrapper.orderByAsc(EventHandle::getHandleTime);
        return eventHandleMapper.selectList(wrapper);
    }

    /**
     * 我的待处理事件（指派给我、尚未终态）
     */
    public List<EventInfo> myPending(Long userId) {
        LambdaQueryWrapper<EventInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventInfo::getHandlerId, userId);
        wrapper.in(EventInfo::getStatus, STATUS_PENDING, STATUS_PROCESSING);
        wrapper.orderByAsc(EventInfo::getUrgency).orderByDesc(EventInfo::getCreateTime);
        return eventInfoMapper.selectList(wrapper);
    }

    /**
     * 我创建的事件
     */
    public List<EventInfo> myCreated(Long userId) {
        LambdaQueryWrapper<EventInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventInfo::getApplicantId, userId);
        wrapper.orderByDesc(EventInfo::getCreateTime);
        return eventInfoMapper.selectList(wrapper);
    }

    private void recordHandle(Long eventId, String handleType, String desc, String result) {
        EventHandle record = new EventHandle();
        record.setEventId(eventId);
        record.setHandleType(handleType);
        record.setHandleDesc(desc);
        LoginUser loginUser = LoginUser.current();
        record.setHandlerId(loginUser != null ? loginUser.getUserId() : null);
        record.setHandleTime(LocalDateTime.now());
        record.setHandleResult(result);
        eventHandleMapper.insert(record);
    }

    private void requireStatus(EventInfo event, int... allowed) {
        for (int s : allowed) {
            if (event.getStatus() == s) {
                return;
            }
        }
        throw new BusinessException(409, "当前状态下不允许此操作: " + statusName(event.getStatus()));
    }

    public static String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PENDING -> "待审批";
            case STATUS_PROCESSING -> "处理中";
            case STATUS_DONE -> "已完成";
            case STATUS_CLOSED -> "已关闭";
            case STATUS_REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    public static String typeName(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "售后";
            case 2 -> "变更";
            case 3 -> "异常";
            default -> "其他";
        };
    }

    public static String urgencyName(Integer urgency) {
        if (urgency == null) return "一般";
        return switch (urgency) {
            case 1 -> "紧急";
            case 3 -> "低";
            default -> "一般";
        };
    }
}
