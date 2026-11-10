package com.dept.mgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.mgmt.platform.code.entity.CodeSeq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 编码序号 Mapper
 * increment 为原子自增：行锁由 UPDATE 语句保证，需在独立短事务（REQUIRES_NEW）中调用
 *
 * @author dept-mgmt
 */
@Mapper
public interface CodeSeqMapper extends BaseMapper<CodeSeq> {

    /**
     * 原子自增当前流水值
     *
     * @return 受影响行数：1=成功；0=该 (rule_code, date_part) 尚无记录（调用方初始化后重试）
     */
    @Update("UPDATE sys_code_seq SET current_value = current_value + 1 " +
            "WHERE rule_code = #{ruleCode} AND date_part = #{datePart}")
    int increment(@Param("ruleCode") String ruleCode, @Param("datePart") String datePart);
}
