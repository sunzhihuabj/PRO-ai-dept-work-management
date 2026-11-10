package com.dept.mgmt.common.result;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * 统一响应结果封装
 *
 * @author dept-mgmt
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 数据 */
    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static <T> R<T> ok() {
        return new R<>(HttpStatus.OK.value(), "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(HttpStatus.OK.value(), "success", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(HttpStatus.OK.value(), msg, data);
    }

    /**
     * 失败响应
     */
    public static <T> R<T> fail(String msg) {
        return new R<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        return new R<>(code, msg, data);
    }

    public boolean isSuccess() {
        return this.code == HttpStatus.OK.value();
    }
}
