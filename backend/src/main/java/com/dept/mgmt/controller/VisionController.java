package com.dept.mgmt.controller;

import com.dept.mgmt.common.result.R;
import com.dept.mgmt.common.client.AiGatewayClient;
import com.dept.mgmt.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 视觉感知接口（V2.0 E1 眼睛模块）：图片识别 → 结构化描述进对话上下文。
 *
 * <p>链路：前端上传图片（data URL）→ 本接口 → 网关多模态通道（CAP_VISION_DESCRIBE）
 * → 模型视觉识别 → 描述文本返回前端注入对话。图片经网关脱敏豁免（本地通道），
 * 审计入 sys_ai_call_log（capability=CAP_VISION_DESCRIBE）。
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/ai/vision")
@Tag(name = "AI 视觉感知", description = "V2.0 E1 眼睛模块：图片识别与描述")
public class VisionController {

    private static final int MAX_IMAGES = 4;

    private final AiGatewayClient aiGatewayClient;

    public VisionController(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    /**
     * 图片描述/识别（V2.0 E1）。
     *
     * @param body images: data URL 列表（≤4 张，单张 base64 ≤5MB）；prompt: 识别指令（可空=默认描述）
     */
    @PostMapping("/describe")
    @Operation(summary = "图片识别与描述（E1 眼睛）")
    public R<String> describe(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) body.get("images");
        String prompt = body.get("prompt") == null ? null : String.valueOf(body.get("prompt"));
        if (images == null || images.isEmpty()) {
            return R.fail(400, "缺少图片（images 必填，data URL 形状，≤4 张）");
        }
        if (images.size() > MAX_IMAGES) {
            return R.fail(400, "单次最多 4 张图片");
        }
        String effectivePrompt = prompt == null || prompt.isBlank()
                ? "请识别并描述这张图片的内容：如为业务表格/单据，请以 Markdown 表格还原全部可见字段与数据；"
                        + "如为照片/截图，请说明关键对象、文字与上下文。500 字内。"
                : prompt;
        LoginUser user = LoginUser.current();
        String desc = aiGatewayClient.visionChat("CAP_VISION_DESCRIBE", effectivePrompt,
                images, user == null ? null : user.getUserId());
        return R.ok(desc);
    }
}
