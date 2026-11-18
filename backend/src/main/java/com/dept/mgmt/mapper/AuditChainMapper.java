package com.dept.mgmt.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 铁律五审计链联查 Mapper（V2.0 D7）：v_audit_chain 视图四段联查
 * （RUN_EVENT 过程事件 / TOOL_AUDIT 工具审计 / AI_CALL 模型调用 / OUTBOUND 出网留痕）。
 *
 * @author dept-mgmt
 */
@Mapper
public interface AuditChainMapper {

    /** 按 runId 精确 + 可选用户/时间窗过滤，时间倒序 */
    @Select("""
            <script>
            SELECT chain_type, run_id AS runId, ref_id AS refId, action, detail_key AS detailKey,
                   detail, is_error AS isError, duration_ms AS durationMs, user_id AS userId,
                   create_time AS createTime
            FROM v_audit_chain
            <where>
                <if test="runId != null and runId != ''">AND run_id = #{runId}</if>
                <if test="userId != null">AND user_id = #{userId}</if>
                <if test="from != null">AND create_time &gt;= #{from}</if>
                <if test="to != null">AND create_time &lt;= #{to}</if>
            </where>
            ORDER BY create_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> chain(String runId, Long userId,
                                    java.time.LocalDateTime from, java.time.LocalDateTime to, int limit);
}
