package com.dept.mgmt.controller;

import com.dept.mgmt.board.entity.Redcard;
import com.dept.mgmt.board.entity.RedcardRule;
import com.dept.mgmt.board.service.BoardsService;
import com.dept.mgmt.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * M1 看板接口（T-209）：九板数据集 + 三级视图 + 红灯引擎 + 会议模式
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理看板", description = "九板/红灯引擎/会议模式（M1）")
public class BoardController {

    private final BoardsService boardsService;

    @GetMapping("/boards")
    @Operation(summary = "九板数据集（三级视图：company 全部九板 / board 单板 / project 单项目）")
    public R<Map<String, Object>> boards(@RequestParam(defaultValue = "company") String viewLevel,
                                         @RequestParam(required = false) String board,
                                         @RequestParam(required = false) Long projectId) {
        return R.ok(boardsService.boards(viewLevel, board, projectId));
    }

    @PostMapping("/redcards/evaluate")
    @Operation(summary = "执行红灯引擎评估（触发/升级/自动消除，规则配置化）")
    public R<Map<String, Object>> evaluate() {
        return R.ok("评估完成", boardsService.evaluateRedcards());
    }

    @GetMapping("/redcards")
    @Operation(summary = "红灯记录列表（按状态/板块）")
    public R<List<Redcard>> redcards(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String board) {
        return R.ok(boardsService.listRedcards(status, board));
    }

    @PostMapping("/redcards/{id}/attend")
    @Operation(summary = "红灯会议过堂（指定责任人与期限 → ASSIGNED）")
    public R<Void> attend(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boardsService.attendRedcard(id,
                (String) body.get("owner"),
                body.get("dueDate") == null ? null : LocalDate.parse(String.valueOf(body.get("dueDate"))));
        return R.ok("过堂完成", null);
    }

    @PostMapping("/redcards/{id}/resolve")
    @Operation(summary = "红灯消除")
    public R<Void> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        boardsService.resolveRedcard(id, body.get("note"));
        return R.ok("红灯已消除", null);
    }

    @GetMapping("/redcards/rules")
    @Operation(summary = "红灯规则配置列表")
    public R<List<RedcardRule>> rules() {
        return R.ok(boardsService.listRules());
    }

    @GetMapping("/meeting/mode")
    @Operation(summary = "会议模式开关状态")
    public R<Map<String, Object>> meetingMode() {
        return R.ok(Map.of("enabled", boardsService.isMeetingMode()));
    }

    @PostMapping("/meeting/mode")
    @Operation(summary = "切换会议模式（早会红灯过堂）")
    public R<Map<String, Object>> toggleMeetingMode(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        boardsService.setMeetingMode(enabled);
        return R.ok("已切换", Map.of("enabled", enabled));
    }
}
