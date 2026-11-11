package com.dept.mgmt.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.archive.entity.ArchiveBorrow;
import com.dept.mgmt.archive.entity.ArchiveInfo;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.ArchiveBorrowMapper;
import com.dept.mgmt.mapper.ArchiveInfoMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * M3 档案管理服务（T-305）
 * 档号规则（DA-{yyyy}-{seq}）；电子实物双轨（纸质/双轨必填存放位置）；
 * 借阅（NORMAL→BORROWED，一人一档不可重复借出）；归还；逾期检测；鉴定销毁
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private static final List<String> ARCHIVE_TYPES = List.of("CONTRACT", "SCHEME", "DRAWING", "RECORD", "ACCEPTANCE");
    private static final List<String> MEDIUMS = List.of("ELECTRONIC", "PAPER", "DUAL");

    private final ArchiveInfoMapper archiveMapper;
    private final ArchiveBorrowMapper borrowMapper;
    private final CodeEngineService codeEngineService;

    @Transactional(rollbackFor = Exception.class)
    public Long registerArchive(Long projectId, String archiveType, String title, String medium,
                                String location, String remark) {
        if (!ARCHIVE_TYPES.contains(archiveType)) {
            throw new BusinessException(400, "档案类型仅允许 " + ARCHIVE_TYPES);
        }
        if (!MEDIUMS.contains(medium)) {
            throw new BusinessException(400, "载体仅允许 ELECTRONIC/PAPER/DUAL");
        }
        // 电子实物双轨规则：纸质/双轨必须登记实物存放位置
        if (!"ELECTRONIC".equals(medium) && (location == null || location.isBlank())) {
            throw new BusinessException(400, "纸质/双轨档案必须登记实物存放位置（双轨归档规则）");
        }
        ArchiveInfo archive = new ArchiveInfo();
        archive.setArchiveNo(codeEngineService.nextCode("DA", null));
        archive.setProjectId(projectId);
        archive.setArchiveType(archiveType);
        archive.setTitle(title);
        archive.setMedium(medium);
        archive.setLocation(location);
        archive.setStatus("NORMAL");
        archive.setRemark(remark);
        archiveMapper.insert(archive);
        log.info("档案归档: {} {} ({})", archive.getArchiveNo(), title, medium);
        return archive.getId();
    }

    /** 借阅（同档不可重复借出；纸质/双轨才允许实物借出） */
    @Transactional(rollbackFor = Exception.class)
    public Long borrowArchive(Long archiveId, String borrower, String purpose, int dueDays, String approveBy) {
        ArchiveInfo archive = mustExistArchive(archiveId);
        if (!"NORMAL".equals(archive.getStatus())) {
            throw new BusinessException(400, "档案当前不可借阅，状态: " + archive.getStatus());
        }
        if ("ELECTRONIC".equals(archive.getMedium())) {
            throw new BusinessException(400, "电子载体档案无需实物借阅，请直接查阅电子版本");
        }
        if (borrower == null || borrower.isBlank()) {
            throw new BusinessException(400, "借阅人必填");
        }
        ArchiveBorrow borrow = new ArchiveBorrow();
        borrow.setArchiveId(archiveId);
        borrow.setBorrower(borrower);
        borrow.setPurpose(purpose);
        borrow.setBorrowDate(LocalDate.now());
        borrow.setDueDate(LocalDate.now().plusDays(dueDays <= 0 ? 7 : dueDays));
        borrow.setStatus("BORROWED");
        borrow.setApproveBy(approveBy);
        borrowMapper.insert(borrow);
        archive.setStatus("BORROWED");
        archiveMapper.updateById(archive);
        log.info("档案借阅: {} → {} 应还 {}", archive.getArchiveNo(), borrower, borrow.getDueDate());
        return borrow.getId();
    }

    /** 归还（BORROWED→NORMAL，借阅单记录归还日期；逾期归还标记 OVERDUE） */
    @Transactional(rollbackFor = Exception.class)
    public void returnArchive(Long borrowId) {
        ArchiveBorrow borrow = borrowMapper.selectById(borrowId);
        if (borrow == null) {
            throw new BusinessException(404, "借阅记录不存在: " + borrowId);
        }
        if (!"BORROWED".equals(borrow.getStatus())) {
            throw new BusinessException(400, "借阅记录已完成归还，不允许重复归还，当前: " + borrow.getStatus());
        }
        borrow.setReturnDate(LocalDate.now());
        borrow.setStatus(LocalDate.now().isAfter(borrow.getDueDate()) ? "OVERDUE" : "RETURNED");
        borrowMapper.updateById(borrow);
        ArchiveInfo archive = mustExistArchive(borrow.getArchiveId());
        archive.setStatus("NORMAL");
        archiveMapper.updateById(archive);
        log.info("档案归还: {} 状态 {}", borrow.getId(), borrow.getStatus());
    }

    /** 逾期借阅清单（应还日期已过未归还） */
    public List<ArchiveBorrow> overdueBorrows() {
        return borrowMapper.selectList(new LambdaQueryWrapper<ArchiveBorrow>()
                .eq(ArchiveBorrow::getStatus, "BORROWED")
                .lt(ArchiveBorrow::getDueDate, LocalDate.now()));
    }

    /** 鉴定销毁（NORMAL 且无在借） */
    @Transactional(rollbackFor = Exception.class)
    public void destroyArchive(Long archiveId, String remark) {
        ArchiveInfo archive = mustExistArchive(archiveId);
        if (!"NORMAL".equals(archive.getStatus())) {
            throw new BusinessException(400, "仅正常在库档案可鉴定销毁，当前: " + archive.getStatus());
        }
        if (remark == null || remark.isBlank()) {
            throw new BusinessException(400, "鉴定销毁必须填写鉴定意见");
        }
        archive.setStatus("DESTROYED");
        archive.setRemark(remark);
        archiveMapper.updateById(archive);
        log.info("档案鉴定销毁: {} 意见 {}", archive.getArchiveNo(), remark);
    }

    public PageResult<ArchiveInfo> listArchives(int pageNum, int pageSize, Long projectId, String status) {
        LambdaQueryWrapper<ArchiveInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, ArchiveInfo::getProjectId, projectId);
        wrapper.eq(status != null && !status.isBlank(), ArchiveInfo::getStatus, status);
        wrapper.orderByDesc(ArchiveInfo::getId);
        return PageResult.of(archiveMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public List<ArchiveBorrow> listBorrows(Long archiveId) {
        return borrowMapper.selectList(new LambdaQueryWrapper<ArchiveBorrow>()
                .eq(archiveId != null, ArchiveBorrow::getArchiveId, archiveId)
                .orderByDesc(ArchiveBorrow::getId));
    }

    private ArchiveInfo mustExistArchive(Long id) {
        ArchiveInfo archive = archiveMapper.selectById(id);
        if (archive == null) {
            throw new BusinessException(404, "档案不存在: " + id);
        }
        return archive;
    }
}
