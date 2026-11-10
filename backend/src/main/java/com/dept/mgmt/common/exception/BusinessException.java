package com.dept.mgmt.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于业务逻辑中需要中断流程并返回特定错误码的场景
 *
 * @author dept-mgmt
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
