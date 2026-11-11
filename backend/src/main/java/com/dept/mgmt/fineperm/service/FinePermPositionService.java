package com.dept.mgmt.fineperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.fineperm.dto.UserPositionDTO;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import com.dept.mgmt.fineperm.entity.SysPosition;
import com.dept.mgmt.fineperm.entity.SysUserPosition;
import com.dept.mgmt.mapper.SysPositionMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.mapper.SysUserPositionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 职位服务（T-404）
 * 职位定义 CRUD + 用户职位分配（固定/临时，临时带有效期校验）
 * 每次影响用户权限的变更：记录变更日志（subject=USER）+ 递增权限版本号
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePermPositionService {

    private final SysPositionMapper positionMapper;
    private final SysUserPositionMapper userPositionMapper;
    private final SysUserMapper sysUserMapper;
    private final FinePermCacheService cacheService;
    private final FinePermChangeLogService changeLogService;

    /**
     * 职位视图（含在持人数）
     */
    @Data
    public static class PositionVO {
        private Long id;
        private String positionCode;
        private String positionName;
        private String description;
        private Integer status;
        private LocalDateTime createTime;
        /** 已分配该职位的用户数（含固定/临时） */
        private Long holderCount;
    }

    /**
     * 用户职位视图（含职位名称）
     */
    @Data
    public static class UserPositionVO {
        private Long id;
        private Long userId;
        private Long positionId;
        private String positionName;
        private String positionCode;
        private String positionKind;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        /** 是否当前有效（TEMP 过期判断） */
        private boolean effective;
    }

    /**
     * 职位列表（含在持人数）
     */
    public List<PositionVO> listPositions() {
        List<SysPosition> positions = positionMapper.selectList(new LambdaQueryWrapper<SysPosition>()
                .orderByAsc(SysPosition::getId));
        List<SysUserPosition> allUserPositions = userPositionMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Long> holderCount = allUserPositions.stream()
                .collect(Collectors.groupingBy(SysUserPosition::getPositionId, Collectors.counting()));
        List<PositionVO> vos = new ArrayList<>();
        for (SysPosition p : positions) {
            PositionVO vo = new PositionVO();
            vo.setId(p.getId());
            vo.setPositionCode(p.getPositionCode());
            vo.setPositionName(p.getPositionName());
            vo.setDescription(p.getDescription());
            vo.setStatus(p.getStatus());
            vo.setCreateTime(p.getCreateTime());
            vo.setHolderCount(holderCount.getOrDefault(p.getId(), 0L));
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 创建职位
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPosition(SysPosition position) {
        if (position.getPositionCode() == null || position.getPositionCode().isBlank()) {
            throw new BusinessException(400, "职位编码不能为空");
        }
        Long count = positionMapper.selectCount(new LambdaQueryWrapper<SysPosition>()
                .eq(SysPosition::getPositionCode, position.getPositionCode()));
        if (count != null && count > 0) {
            throw new BusinessException(400, "职位编码已存在: " + position.getPositionCode());
        }
        if (position.getStatus() == null) {
            position.setStatus(1);
        }
        positionMapper.insert(position);
        return position.getId();
    }

    /**
     * 更新职位
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePosition(Long id, SysPosition update) {
        SysPosition existing = positionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "职位不存在: " + id);
        }
        if (update.getPositionName() != null) {
            existing.setPositionName(update.getPositionName());
        }
        if (update.getDescription() != null) {
            existing.setDescription(update.getDescription());
        }
        if (update.getStatus() != null) {
            existing.setStatus(update.getStatus());
        }
        positionMapper.updateById(existing);
        // 职位停用影响权限 → 版本递增
        cacheService.bump();
    }

    /**
     * 删除职位（需先解除用户关联）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePosition(Long id) {
        Long holders = userPositionMapper.selectCount(new LambdaQueryWrapper<SysUserPosition>()
                .eq(SysUserPosition::getPositionId, id));
        if (holders != null && holders > 0) {
            throw new BusinessException(400, "该职位仍被 " + holders + " 个用户持有，请先解除关联");
        }
        positionMapper.deleteById(id);
    }

    /**
     * 用户的职位列表
     */
    public List<UserPositionVO> listUserPositions(Long userId) {
        List<SysUserPosition> ups = userPositionMapper.selectList(new LambdaQueryWrapper<SysUserPosition>()
                .eq(SysUserPosition::getUserId, userId)
                .orderByDesc(SysUserPosition::getId));
        LocalDateTime now = LocalDateTime.now();
        List<UserPositionVO> vos = new ArrayList<>();
        for (SysUserPosition up : ups) {
            UserPositionVO vo = new UserPositionVO();
            vo.setId(up.getId());
            vo.setUserId(up.getUserId());
            vo.setPositionId(up.getPositionId());
            vo.setPositionKind(up.getPositionKind());
            vo.setStartTime(up.getStartTime());
            vo.setEndTime(up.getEndTime());
            SysPosition p = positionMapper.selectById(up.getPositionId());
            if (p != null) {
                vo.setPositionName(p.getPositionName());
                vo.setPositionCode(p.getPositionCode());
            }
            boolean effective = SysUserPosition.KIND_FIXED.equals(up.getPositionKind())
                    || (up.getStartTime() != null && up.getEndTime() != null
                    && !now.isBefore(up.getStartTime()) && !now.isAfter(up.getEndTime()));
            vo.setEffective(effective);
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 为用户分配职位（FIXED 无需有效期；TEMP 必须 start<end）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long assignUserPosition(UserPositionDTO dto) {
        SysUser user = sysUserMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在: " + dto.getUserId());
        }
        SysPosition position = positionMapper.selectById(dto.getPositionId());
        if (position == null) {
            throw new BusinessException(404, "职位不存在: " + dto.getPositionId());
        }
        boolean isTemp = SysUserPosition.KIND_TEMP.equals(dto.getPositionKind());
        boolean isFixed = SysUserPosition.KIND_FIXED.equals(dto.getPositionKind());
        if (!isTemp && !isFixed) {
            throw new BusinessException(400, "职位性质非法: " + dto.getPositionKind());
        }
        if (isTemp) {
            if (dto.getStartTime() == null || dto.getEndTime() == null) {
                throw new BusinessException(400, "临时职位必须配置生效起止时间");
            }
            if (!dto.getStartTime().isBefore(dto.getEndTime())) {
                throw new BusinessException(400, "临时职位开始时间必须早于结束时间");
            }
        }

        // 同用户同职位去重
        Long dup = userPositionMapper.selectCount(new LambdaQueryWrapper<SysUserPosition>()
                .eq(SysUserPosition::getUserId, dto.getUserId())
                .eq(SysUserPosition::getPositionId, dto.getPositionId()));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "该用户已持有此职位");
        }

        SysUserPosition up = new SysUserPosition();
        up.setUserId(dto.getUserId());
        up.setPositionId(dto.getPositionId());
        up.setPositionKind(dto.getPositionKind());
        up.setStartTime(dto.getStartTime());
        up.setEndTime(dto.getEndTime());
        userPositionMapper.insert(up);

        // 日志（USER 视角）+ 版本递增
        List<String> before = userPositionSnapshot(dto.getUserId(), null);
        List<String> after = userPositionSnapshot(dto.getUserId(), up);
        changeLogService.record(SysPermChangeLog.SUBJECT_USER, dto.getUserId(), user.getRealName(),
                isTemp ? "TEMP" : "FIXED", "ASSIGN_POSITION",
                List.of(position.getPositionName()), before, after, dto.getRemark());
        cacheService.bump();
        return up.getId();
    }

    /**
     * 移除用户职位关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUserPosition(Long userPositionId, String remark) {
        SysUserPosition up = userPositionMapper.selectById(userPositionId);
        if (up == null) {
            throw new BusinessException(404, "用户职位关联不存在: " + userPositionId);
        }
        SysUser user = sysUserMapper.selectById(up.getUserId());
        SysPosition position = positionMapper.selectById(up.getPositionId());

        List<String> before = userPositionSnapshot(up.getUserId(), null);
        userPositionMapper.deleteById(userPositionId);
        List<String> after = userPositionSnapshot(up.getUserId(), null);

        changeLogService.record(SysPermChangeLog.SUBJECT_USER, up.getUserId(),
                user == null ? null : user.getRealName(),
                SysUserPosition.KIND_TEMP.equals(up.getPositionKind()) ? "TEMP" : "FIXED",
                "REMOVE_POSITION",
                position == null ? List.of() : List.of(position.getPositionName()),
                before, after, remark);
        cacheService.bump();
    }

    /** 用户职位快照（写日志：positionName:kind[有效期]） */
    private List<String> userPositionSnapshot(Long userId, SysUserPosition include) {
        List<SysUserPosition> ups = userPositionMapper.selectList(new LambdaQueryWrapper<SysUserPosition>()
                .eq(SysUserPosition::getUserId, userId));
        List<String> snapshot = ups.stream().map(u -> {
            SysPosition p = positionMapper.selectById(u.getPositionId());
            String name = p == null ? "职位#" + u.getPositionId() : p.getPositionName();
            String s = name + ":" + u.getPositionKind();
            if (SysUserPosition.KIND_TEMP.equals(u.getPositionKind()) && u.getStartTime() != null) {
                s += "[" + u.getStartTime().toLocalDate() + "~" + u.getEndTime().toLocalDate() + "]";
            }
            return s;
        }).collect(Collectors.toList());
        if (include != null && include.getId() == null) {
            // 新插入未回查的场景
            SysPosition p = positionMapper.selectById(include.getPositionId());
            snapshot.add((p == null ? "职位#" + include.getPositionId() : p.getPositionName())
                    + ":" + include.getPositionKind());
        }
        return snapshot;
    }
}
