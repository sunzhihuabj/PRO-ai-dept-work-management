package com.dept.mgmt.controller;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.archive.entity.ArchiveBorrow;
import com.dept.mgmt.archive.entity.ArchiveInfo;
import com.dept.mgmt.archive.service.ArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M3 档案管理接口（T-305）：档号/双轨/借阅/鉴定销毁
 *
 * @author dept-mgmt
 */
@RestController
@RequestMapping("/v1/archive")
@RequiredArgsConstructor
@Tag(name = "档案管理", description = "档号/电子实物双轨/借阅/鉴定销毁（M3）")
public class ArchiveController {

    private final ArchiveService archiveService;

    @PostMapping
    @Operation(summary = "档案登记（DA 档号自动生成；纸质/双轨必填存放位置）")
    @OperLog(title = "档案管理", businessType = 1)
    public R<Long> register(@RequestBody Map<String, Object> body) {
        return R.ok("档案已登记", archiveService.registerArchive(
                Long.valueOf(String.valueOf(body.get("projectId"))),
                (String) body.get("archiveType"),
                (String) body.get("title"),
                (String) body.getOrDefault("medium", "ELECTRONIC"),
                (String) body.get("location"),
                (String) body.get("remark")));
    }

    @GetMapping
    @Operation(summary = "档案分页")
    public R<PageResult<ArchiveInfo>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) Long projectId,
                                           @RequestParam(required = false) String status) {
        return R.ok(archiveService.listArchives(pageNum, pageSize, projectId, status));
    }

    @PostMapping("/{id}/borrow")
    @Operation(summary = "档案借阅（电子载体拒绝实物借出；同档不可重复借出）")
    public R<Long> borrow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok("借阅已登记", archiveService.borrowArchive(
                id,
                (String) body.get("borrower"),
                (String) body.get("purpose"),
                body.get("dueDays") == null ? 7 : Integer.parseInt(String.valueOf(body.get("dueDays"))),
                (String) body.get("approveBy")));
    }

    @PostMapping("/borrows/{id}/return")
    @Operation(summary = "档案归还（逾期自动标记 OVERDUE）")
    public R<Void> returnArchive(@PathVariable Long id) {
        archiveService.returnArchive(id);
        return R.ok("归还完成", null);
    }

    @GetMapping("/borrows/overdue")
    @Operation(summary = "逾期借阅清单")
    public R<List<ArchiveBorrow>> overdue() {
        return R.ok(archiveService.overdueBorrows());
    }

    @GetMapping("/borrows")
    @Operation(summary = "借阅记录（按档案）")
    public R<List<ArchiveBorrow>> borrows(@RequestParam Long archiveId) {
        return R.ok(archiveService.listBorrows(archiveId));
    }

    @PostMapping("/{id}/destroy")
    @Operation(summary = "鉴定销毁（鉴定意见必填）")
    @OperLog(title = "档案管理", businessType = 3)
    public R<Void> destroy(@PathVariable Long id, @RequestBody Map<String, String> body) {
        archiveService.destroyArchive(id, body.get("remark"));
        return R.ok("销毁完成", null);
    }
}
