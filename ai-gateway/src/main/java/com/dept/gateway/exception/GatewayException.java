package com.dept.gateway.exception;

import lombok.Getter;

/**
 * 网关业务异常（HTTP 语义错误码直接透传给调用方）
 *
 * @author dept-mgmt
 */
@Getter
public class GatewayException extends RuntimeException {

    private final int status;

    public GatewayException(int status, String message) {
        super(message);
        this.status = status;
    }
}
