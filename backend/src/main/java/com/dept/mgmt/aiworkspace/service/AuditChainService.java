package com.dept.mgmt.aiworkspace.service;

import com.dept.mgmt.mapper.AuditChainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计链联查服务（V2.0 铁律五/D7）：run_id 贯通 过程事件→工具审计→模型调用→出网留痕。
 *
 * @author dept-mgmt
 */
@Service
@RequiredArgsConstructor
public class AuditChainService {

    private final AuditChainMapper auditChainMapper;

    /**
     * 审计链查询：runId 精确贯通；或按用户+时间窗检索。默认最多 200 行。
     */
    public Map<String, Object> chain(String runId, Long userId, LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> rows = auditChainMapper.chain(
                (runId == null || runId.isBlank()) ? null : runId, userId, from, to, 200);
        long errorCount = rows.stream().filter(r -> Integer.valueOf(1).equals(
                ((Number) r.getOrDefault("isError", 0)).intValue())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("total", rows.size());
        result.put("errorCount", errorCount);
        result.put("rows", rows);
        return result;
    }
}
