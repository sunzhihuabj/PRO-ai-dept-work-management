package com.dept.mgmt.archive.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 档案借阅记录实体（T-305 M3）
 *
 * @author dept-mgmt
 */
@Data
@TableName("archive_borrow")
public class ArchiveBorrow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 借阅ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档案ID */
    private Long archiveId;

    /** 借阅人 */
    private String borrower;

    /** 借阅用途 */
    private String purpose;

    /** 借出日期 */
    private LocalDate borrowDate;

    /** 应还日期 */
    private LocalDate dueDate;

    /** 实际归还日期 */
    private LocalDate returnDate;

    /** 状态（BORROWED借出/RETURNED已归还/OVERDUE逾期） */
    private String status;

    /** 批准人 */
    private String approveBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
