package com.dept.mgmt.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 * 对应前端统一分页响应结构：records/total/size/current/pages
 *
 * @author dept-mgmt
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private List<T> records;

    /** 总数 */
    private long total;

    /** 每页条数 */
    private long size;

    /** 当前页 */
    private long current;

    /** 总页数 */
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long size, long current, long pages) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = pages;
    }

    /**
     * 从MyBatis-Plus IPage构建分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                page.getTotal(),
                page.getSize(),
                page.getCurrent(),
                page.getPages()
        );
    }

    /**
     * 空结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0L, 10L, 1L, 0L);
    }
}
