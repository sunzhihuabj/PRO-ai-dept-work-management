package com.dept.mgmt.agent.handler;

import com.dept.mgmt.chatbi.service.ChatBiService;
import com.dept.mgmt.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatBI 数据问答处理器（AGT-25，T-307）
 * 指标口径白名单 + NL 受限生成（LLM 仅做"选指标"，不生成 SQL）+ 禁止编造校验
 * 指令令牌约定：question=xxx 或 question=xxx&projectId=N
 * 只读查询不改任何业务状态；V2 定义为 Ask 级（回答即结果，无草稿落库），与 AGT-26 同为只读自动执行
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBiAskHandler implements CapabilityHandler {

    private static final Pattern QUESTION = Pattern.compile("question\\s*=\\s*(.+?)(?:&|$)");
    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*=\\s*(\\d+)");

    private final ChatBiService chatBiService;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-25";
    }

    /** 纯只读查询（指标白名单内），不改任何业务状态，可自动执行 */
    @Override
    public boolean requireConfirm() {
        return false;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException(400, "指令中未含 question=，无法进行数据问答");
        }
        Matcher q = QUESTION.matcher(instruction);
        if (!q.find()) {
            throw new BusinessException(400, "指令中未含 question=，无法进行数据问答");
        }
        String question = q.group(1).trim();
        Map<String, Object> params = new LinkedHashMap<>();
        Matcher p = PROJECT_ID.matcher(instruction);
        if (p.find()) {
            params.put("projectId", Long.valueOf(p.group(1)));
        }
        Map<String, Object> result = chatBiService.ask(question, params, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-25 ChatBI 数据问答");
        out.putAll(result);
        log.info("AGT-25 数据问答完成: question={} → {}", question, result.get("summary"));
        return objectMapper.writeValueAsString(out);
    }
}
