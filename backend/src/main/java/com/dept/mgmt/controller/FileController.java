package com.dept.mgmt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.FileInfo;
import com.dept.mgmt.mapper.FileInfoMapper;
import com.dept.mgmt.service.FileService;
import com.dept.mgmt.common.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.dept.mgmt.dto.FileAuditDTO;
import com.dept.mgmt.entity.FileAudit;
import com.dept.mgmt.service.FileAuditService;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

/**
 * 档案归档 - 文件上传、查询、下载接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "档案归档", description = "文件上传、查询、下载等接口")
public class FileController {

    private final FileService fileService;
    private final FileInfoMapper fileInfoMapper;
    private final FileAuditService fileAuditService;
    private final com.dept.mgmt.mapper.FileVersionMapper fileVersionMapper;
    private final com.dept.mgmt.mapper.ArchiveRecordMapper archiveRecordMapper;
    private final com.dept.mgmt.service.AiService aiService;

    @Value("${file.local.upload-path}")
    private String uploadRootPath;

    @PostMapping("/upload")
    @Operation(summary = "上传文件（phaseId/taskId 可选：taskId=任务资料，按任务名重命名并归属项目）")
    @OperLog(title = "档案归档", businessType = 1)
    public R<FileInfo> upload(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long phaseId,
            @RequestParam(required = false) Long taskId,
            @RequestParam String fileType,
            @RequestParam String description,
            @RequestPart("file") MultipartFile file) {
        FileInfo saved = fileService.uploadFile(projectId, phaseId, taskId, fileType, description, file);
        return R.ok("文件上传成功", saved);
    }

    @GetMapping
    @Operation(summary = "根据项目/阶段/任务或关键字分页查询文件列表")
    public R<com.dept.mgmt.common.result.PageResult<FileInfo>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long phaseId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileInfo> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, FileInfo::getProjectId, projectId);
        wrapper.eq(phaseId != null, FileInfo::getPhaseId, phaseId);
        wrapper.eq(taskId != null, FileInfo::getTaskId, taskId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(FileInfo::getFileName, keyword)
                              .or()
                              .like(FileInfo::getDescription, keyword));
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        fileInfoMapper.selectPage(page, wrapper);
        return R.ok(com.dept.mgmt.common.result.PageResult.of(page));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载文件（根据文件ID）")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        // filePath 在数据库中保存的是可访问的 URL（以 /files 为前缀），我们需要将其映射到实际磁盘路径
        String urlPrefix = fileInfo.getFilePath().startsWith("/files") ? "/files" : "";
        String relative = fileInfo.getFilePath().replaceFirst("^" + urlPrefix, "");
        // 移除可能的前导斜杠
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        // 拼接到本地根目录，使用注入的 uploadRootPath
        File file = new File(uploadRootPath, relative);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getFileName() + "\"")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取文件详情")
    public R<FileInfo> get(@PathVariable Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null) {
            return R.fail(404, "文件不存在");
        }
        return R.ok(fileInfo);
    }
    
    @PutMapping("/{id}/audit")
    @Operation(summary = "文件审核（通过/驳回）")
    @OperLog(title = "档案归档", businessType = 2)
    public R<Void> audit(@PathVariable Long id, @RequestBody FileAuditDTO dto) {
        fileAuditService.auditFile(id, dto);
        return R.ok("审核完成", null);
    }
    
    @GetMapping("/{id}/audits")
    @Operation(summary = "获取文件的审核记录")
    public R<List<FileAudit>> auditList(@PathVariable Long id) {
        List<FileAudit> list = fileAuditService.listAuditsByFileId(id);
        return R.ok(list);
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "归档文件（标记为已归档）")
    @OperLog(title = "档案归档", businessType = 2)
    public R<Void> archive(@PathVariable Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null) {
            return R.fail(404, "文件不存在");
        }
        if (fileInfo.getIsArchive() != null && fileInfo.getIsArchive() == 1) {
            return R.fail(400, "文件已归档");
        }
        fileInfo.setIsArchive(1);
        fileInfo.setArchiveTime(java.time.LocalDateTime.now());
        fileInfoMapper.updateById(fileInfo);
        // 同步写归档台账
        com.dept.mgmt.entity.ArchiveRecord record = new com.dept.mgmt.entity.ArchiveRecord();
        record.setProjectId(fileInfo.getProjectId());
        record.setFileId(id);
        record.setArchiveType("电子");
        record.setArchiveStatus(1);
        record.setArchiveBy(com.dept.mgmt.security.LoginUser.current() != null
                ? com.dept.mgmt.security.LoginUser.current().getUsername() : "system");
        record.setArchiveTime(fileInfo.getArchiveTime());
        record.setDescription("文件归档自动登记");
        archiveRecordMapper.insert(record);
        log.info("归档台账已登记: fileId={}", id);
        return R.ok("文件已归档", null);
    }

    /** 可做内容级 diff 的文本扩展名 */
    private static final List<String> TEXT_EXTS = List.of(
            "txt", "md", "csv", "log", "json", "xml", "html", "htm", "yml", "yaml",
            "properties", "sql", "java", "js", "ts", "vue", "py", "css", "scss"
    );

    @PostMapping("/{id}/diff")
    @Operation(summary = "AI 分析当前版本与上一版本的差异")
    public R<java.util.Map<String, String>> diff(@PathVariable Long id) {
        FileInfo cur = fileInfoMapper.selectById(id);
        if (cur == null) {
            return R.fail(404, "文件不存在");
        }
        // 找同维度（同 fileName + 同 taskId/phaseId）的上一版本
        LambdaQueryWrapper<FileInfo> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.eq(FileInfo::getFileName, cur.getFileName())
                .eq(cur.getTaskId() != null, FileInfo::getTaskId, cur.getTaskId())
                .eq(cur.getPhaseId() != null, FileInfo::getPhaseId, cur.getPhaseId())
                .isNull(cur.getTaskId() == null && cur.getPhaseId() == null, FileInfo::getPhaseId)
                .lt(FileInfo::getVersionNumber, cur.getVersionNumber())
                .orderByDesc(FileInfo::getVersionNumber)
                .last("LIMIT 1");
        FileInfo prev = fileInfoMapper.selectOne(prevWrapper);
        if (prev == null) {
            return R.fail(404, "该文件没有历史版本可对比");
        }

        String ext = cur.getFileType() == null ? "" : cur.getFileType().toLowerCase();
        if (!TEXT_EXTS.contains(ext)) {
            return R.fail(400, "当前仅支持文本类文件（" + ext + " 不支持），非文本文件请对比元数据（大小/版本）");
        }

        try {
            String prevContent = readLocal(prev);
            String curContent = readLocal(cur);
            String prompt = "你是文件版本差异分析助手。以下是同一文件的两个版本内容，"
                    + "请用中文输出：1) 主要改动点 2) 新增内容 3) 删除内容 4) 潜在风险或注意事项。"
                    + "\n\n===== 旧版本 " + prev.getVersion() + " =====\n" + prevContent
                    + "\n\n===== 新版本 " + cur.getVersion() + " =====\n" + curContent;
            String analysis = aiService.chat(prompt);
            return R.ok(java.util.Map.of(
                    "prevVersion", prev.getVersion() == null ? "" : prev.getVersion(),
                    "curVersion", cur.getVersion() == null ? "" : cur.getVersion(),
                    "analysis", analysis
            ));
        } catch (com.dept.mgmt.common.exception.BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 差异分析失败", e);
            return R.fail(500, "差异分析失败: " + e.getMessage());
        }
    }

    private String readLocal(FileInfo info) throws java.io.IOException {
        String path = info.getFilePath();
        if (path != null && path.startsWith("/files")) {
            path = path.substring("/files".length());
        }
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        File f = new File(uploadRootPath, path == null ? "" : path);
        if (!f.exists()) {
            throw new com.dept.mgmt.common.exception.BusinessException(404, "文件在服务器上不存在: " + info.getFileName());
        }
        if (f.length() > 1024 * 1024) {
            return java.nio.file.Files.readString(f.toPath()).substring(0, 1024 * 1024) + "\n…（内容过长已截断）";
        }
        return java.nio.file.Files.readString(f.toPath());
    }

    @GetMapping("/archive-records")
    @Operation(summary = "归档台账查询")
    public R<List<com.dept.mgmt.entity.ArchiveRecord>> archiveRecords(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long fileId) {
        LambdaQueryWrapper<com.dept.mgmt.entity.ArchiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, com.dept.mgmt.entity.ArchiveRecord::getProjectId, projectId);
        wrapper.eq(fileId != null, com.dept.mgmt.entity.ArchiveRecord::getFileId, fileId);
        wrapper.orderByDesc(com.dept.mgmt.entity.ArchiveRecord::getArchiveTime);
        return R.ok(archiveRecordMapper.selectList(wrapper));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "获取文件历史版本列表")
    public R<List<com.dept.mgmt.entity.FileVersion>> versions(@PathVariable Long id) {
        com.dept.mgmt.entity.FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null) {
            return R.fail(404, "文件不存在");
        }
        LambdaQueryWrapper<com.dept.mgmt.entity.FileVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.dept.mgmt.entity.FileVersion::getFileId, id);
        wrapper.orderByDesc(com.dept.mgmt.entity.FileVersion::getVersionNumber);
        return R.ok(fileVersionMapper.selectList(wrapper));
    }

    @GetMapping("/versions/{versionId}/download")
    @Operation(summary = "下载指定历史版本")
    public ResponseEntity<FileSystemResource> downloadVersion(@PathVariable Long versionId) {
        com.dept.mgmt.entity.FileVersion version = fileVersionMapper.selectById(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        String relative = version.getFilePath();
        if (relative != null && relative.startsWith("/files")) {
            relative = relative.substring("/files".length());
        }
        if (relative != null && relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        File file = new File(uploadRootPath, relative == null ? "" : relative);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        String fileName = file.getName();
        // 去除前缀时间戳，构造更友好的文件名
        int idx = fileName.indexOf('_');
        String displayName = idx > 0 ? fileName.substring(idx + 1) : fileName;
        if (version.getVersionLabel() != null) {
            displayName = version.getVersionLabel() + "_" + displayName;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + displayName + "\"")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}

