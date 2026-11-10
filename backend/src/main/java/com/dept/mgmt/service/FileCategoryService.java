package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.FileCategoryDTO;
import com.dept.mgmt.entity.FileCategory;
import com.dept.mgmt.mapper.FileCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件分类服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCategoryService {

    private final FileCategoryMapper fileCategoryMapper;

    /** 查询所有分类（平铺，前端自行组树） */
    public List<FileCategory> listAll() {
        LambdaQueryWrapper<FileCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(FileCategory::getOrderNum);
        return fileCategoryMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(FileCategoryDTO dto) {
        FileCategory entity = new FileCategory();
        apply(entity, dto);
        fileCategoryMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FileCategoryDTO dto) {
        FileCategory exists = fileCategoryMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (dto.getParentId() != null && dto.getParentId().equals(id)) {
            throw new BusinessException(400, "父分类不能选择自己");
        }
        apply(exists, dto);
        fileCategoryMapper.updateById(exists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FileCategory exists = fileCategoryMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "分类不存在");
        }
        LambdaQueryWrapper<FileCategory> child = new LambdaQueryWrapper<>();
        child.eq(FileCategory::getParentId, id);
        if (fileCategoryMapper.selectCount(child) > 0) {
            throw new BusinessException(409, "存在子分类，不支持删除");
        }
        fileCategoryMapper.deleteById(id);
    }

    private void apply(FileCategory entity, FileCategoryDTO dto) {
        entity.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        entity.setCategoryName(dto.getCategoryName());
        entity.setCategoryCode(dto.getCategoryCode());
        entity.setOrderNum(dto.getOrderNum() == null ? 0 : dto.getOrderNum());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }
}
