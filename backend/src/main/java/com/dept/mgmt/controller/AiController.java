package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.dto.AiSummaryDTO;
import com.dept.mgmt.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 助手接口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI助手", description = "日报/周报/会议纪要自动摘要")
public class AiController {

    private final AiService aiService;

    @PostMapping("/summary")
    @Operation(summary = "生成摘要")
    public R<Map<String, String>> summary(@Valid @RequestBody AiSummaryDTO dto) {
        String summary = aiService.summarize(dto.getText(), dto.getType());
        return R.ok(Map.of("summary", summary));
    }
}
