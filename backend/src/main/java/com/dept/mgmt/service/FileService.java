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
     * M7 魔数校验：按扩展名核对文件头字节，伪造扩展名的内容直接拒绝。
     * 文本类（txt）无固定文件头放行；dwg 各版本头为 "AC1x" 前缀宽松匹配。
     */
    private void assertMagicMatches(String ext, MultipartFile file) {
        byte[] head = new byte[8];
        int n;
        try (java.io.InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取上传文件内容");
        }
        if (n < 4) {
            return; // 过短文件交由业务侧处理
        }
        StringBuilder hexBuilder = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            hexBuilder.append(String.format("%02X", head[i]));
        }
        String hex = hexBuilder.toString();
        boolean ok = switch (ext) {
            case "pdf" -> hex.startsWith("25504446");
            case "png" -> hex.startsWith("89504E47");
            case "jpg", "jpeg" -> hex.startsWith("FFD8FF");
            case "gif" -> hex.startsWith("47494638");
            case "zip", "docx", "xlsx", "pptx" -> hex.startsWith("504B");
            case "rar" -> hex.startsWith("52617221");
            case "doc", "xls", "ppt" -> hex.startsWith("D0CF11E0");
            case "dwg" -> hex.startsWith("4143"); // AC1x 系列版本头
            case "txt" -> true; // 文本无固定头
            default -> true;
        };
        if (!ok) {
            throw new BusinessException(400, "文件内容与声明的类型不符（扩展名 ." + ext + "），已拒绝上传");
        }
    }

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
        // M7 整改：魔数（文件头）校验——防止伪造扩展名上传可执行/脚本内容
        assertMagicMatches(ext, file);

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
        // H5 路径穿越整改：存储名完全随机化（不含用户可控的原始文件名），展示名 displayBase 仅存库
        String storedFilename = timestamp + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File destFile = new File(targetDir, storedFilename);
        // canonical 归属校验：写入目标必须落在上传根目录内（防御 ..\ 拼接/符号链接逃逸）
        try {
            String destCanonical = destFile.getCanonicalPath();
            String rootCanonical = new File(uploadRootPath).getCanonicalPath() + File.separator;
            if (!destCanonical.startsWith(rootCanonical)) {
                log.warn("上传目标路径越界拦截: dest={}, root={}", destCanonical, rootCanonical);
                throw new BusinessException(400, "非法的存储路径");
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(500, "存储路径校验失败");
        }
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
        // T-407：fileType 参数为归档分类（合同主文件/任务资料/项目资料等），显式传入则采用，否则回落扩展名
        fileInfo.setFileType(fileType != null && !fileType.isBlank() ? fileType : ext);
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
