package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.ArchiveRecord;
import com.dept.mgmt.entity.FileInfo;
import com.dept.mgmt.entity.FileVersion;
import com.dept.mgmt.mapper.FileInfoMapper;
import com.dept.mgmt.mapper.FileVersionMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 文件业务服务，负责文件上传、版本管理等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileInfoMapper fileInfoMapper;
    private final FileVersionMapper fileVersionMapper;
    private final com.dept.mgmt.mapper.ArchiveRecordMapper archiveRecordMapper;
    private final com.dept.mgmt.mapper.TaskMapper taskMapper;

    @Value("${file.storage-type:local}")
    private String storageType; // Currently only 'local' is supported

    @Value("${file.local.upload-path}")
    private String uploadRootPath;

    @Value("${file.local.url-prefix}")
    private String urlPrefix;

    // 允许的文件扩展名（不区分大小写）
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "dwg",
            "jpg", "jpeg", "png", "gif", "zip", "rar"
    );

    /**
     * 上传文件并持久化记录。
     * @param projectId 项目 ID（必填）
     * @param phaseId   阶段 ID（必填）
     * @param fileType  文件业务类型，例如 "需求文档"（必填）
     * @param description 版本说明（必填）
     * @param file   MultipartFile 对象（必填）
     * @return 持久化后的 FileInfo 实体
     */
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadFile(Long projectId, Long phaseId, Long taskId, String fileType, String description, MultipartFile file) {
        // 基本校验（phaseId / taskId 可选：taskId 存在=任务资料，按任务名重命名并归属项目）
        if (projectId == null) {
            throw new BusinessException(400, "projectId 为必填参数");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(400, "无法获取上传文件的文件名");
        }
        // 扩展名校验
        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalFilename.substring(dotIdx + 1).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型: " + ext);
        }

        // 任务资料：按任务名重命名展示名，目录按任务隔离
        String displayBase = originalFilename;
        Long dirKey = phaseId;
        if (taskId != null) {
            com.dept.mgmt.entity.Task task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new BusinessException(404, "任务不存在: " + taskId);
            }
            // 重命名：【任务名】_原文件名
            displayBase = task.getTaskName() + "_" + originalFilename;
            dirKey = taskId; // 任务维度目录与版本
            if (task.getProjectId() != null && !task.getProjectId().equals(projectId)) {
                projectId = task.getProjectId(); // 资料一律归属任务所在项目
            }
        }

        // 存储子目录：任务资料 → <projectId>/<taskId>；阶段文件 → <projectId>/<phaseId>；项目级资料 → <projectId>/资料
        String subDir = taskId != null ? "task-" + dirKey
                : phaseId != null ? String.valueOf(phaseId) : "资料";
        // 本地存储路径：<uploadRootPath>/<projectId>/<subDir>/timestamp_renamedName
        File targetDir = new File(uploadRootPath, projectId + File.separator + subDir);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new BusinessException(500, "创建目录失败: " + targetDir.getAbsolutePath());
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String storedFilename = timestamp + "_" + displayBase;
        File destFile = new File(targetDir, storedFilename);
        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            log.error("文件保存异常", e);
            throw new BusinessException(500, "文件保存失败");
        }

        // 版本号计算：同任务（或同阶段/同项目级资料）内重命名后同名文件递增
        LambdaQueryWrapper<FileInfo> query = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getProjectId, projectId)
                .eq(taskId != null, FileInfo::getTaskId, taskId)
                .eq(taskId == null && phaseId != null, FileInfo::getPhaseId, phaseId)
                .isNull(taskId == null && phaseId == null, FileInfo::getPhaseId)
                .eq(FileInfo::getFileName, displayBase)
                .orderByDesc(FileInfo::getVersionNumber)
                .last("LIMIT 1");
        List<FileInfo> latest = fileInfoMapper.selectList(query);
        int versionNumber = latest.isEmpty() ? 1 : latest.get(0).getVersionNumber() + 1;
        String version = "V" + versionNumber + ".0";

        // 生成可访问 URL（相对路径）
        String relativePath = projectId + "/" + subDir + "/" + storedFilename;
        String fileUrl = urlPrefix + "/" + relativePath.replace(File.separatorChar, '/');

        // 填充实体并持久化
        FileInfo fileInfo = new FileInfo();
        fileInfo.setProjectId(projectId);
        fileInfo.setPhaseId(phaseId);
        fileInfo.setTaskId(taskId);
        // 任务资料展示名 = 任务名_原文件名（按任务名称重命名）
        fileInfo.setFileName(displayBase);
        fileInfo.setFilePath(fileUrl);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileType(ext);
        fileInfo.setVersion(version);
        fileInfo.setVersionNumber(versionNumber);
        fileInfo.setIsArchive(0);
        fileInfo.setArchiveTime(null);
        fileInfo.setDescription(description);
        // creator info
        LoginUser loginUser = LoginUser.current();
        fileInfo.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");

        fileInfoMapper.insert(fileInfo);
        // 记录历史版本信息到 file_version 表
        FileVersion fv = new FileVersion();
        fv.setFileId(fileInfo.getId());
        fv.setVersionNumber(versionNumber);
        fv.setVersionLabel(version);
        fv.setFilePath(fileUrl);
        fv.setFileSize(file.getSize());
        fv.setChangeDesc(description + "（上传）");
        fv.setCreateBy(fileInfo.getCreateBy());
        fileVersionMapper.insert(fv);
        
        log.info("文件上传成功: projectId={}, phaseId={}, taskId={}, fileName={}, version={}", projectId, phaseId, taskId, displayBase, version);
        return fileInfo;
    }
}
