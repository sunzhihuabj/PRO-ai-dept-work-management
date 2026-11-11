package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.archive.entity.ArchiveInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * ArchiveInfo Mapper（T-305 M3 档案管理）
 *
 * @author dept-mgmt
 */
@Mapper
public interface ArchiveInfoMapper extends BaseMapper<ArchiveInfo> {
}
