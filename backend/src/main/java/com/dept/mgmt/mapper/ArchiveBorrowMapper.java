package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.archive.entity.ArchiveBorrow;
import org.apache.ibatis.annotations.Mapper;

/**
 * ArchiveBorrow Mapper（T-305 M3 档案管理）
 *
 * @author dept-mgmt
 */
@Mapper
public interface ArchiveBorrowMapper extends BaseMapper<ArchiveBorrow> {
}
