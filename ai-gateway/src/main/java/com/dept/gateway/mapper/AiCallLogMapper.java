package com.dept.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dept.gateway.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 调用审计 Mapper
 *
 * @author dept-mgmt
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    /**
     * 统计某通道当日成功调用次数（日配额用）
     */
    @Select("SELECT COUNT(*) FROM sys_ai_call_log WHERE provider_type = #{providerType} " +
            "AND status IN ('SUCCESS', 'MOCK', 'CONTRACT_FAILED') " +
            "AND create_time >= CURDATE()")
    int countToday(@Param("providerType") String providerType);

    /**
     * 清理保留期之前的审计日志（默认 2 年）
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM sys_ai_call_log WHERE create_time < #{before}")
    int deleteBefore(@Param("before") java.time.LocalDateTime before);
}
