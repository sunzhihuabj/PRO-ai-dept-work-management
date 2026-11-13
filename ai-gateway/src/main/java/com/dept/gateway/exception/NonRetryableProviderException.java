package com.dept.gateway.exception;

/**
 * 不可重试的 Provider 调用失败（T-410 修复）：
 * 认证失败(401/403)、请求参数非法(400)、资源不存在(404)、连接拒绝等——
 * 重试必然再次失败，降级链应立即切换下一通道，避免翻倍耗时与限流配额浪费。
 *
 * @author dept-mgmt
 */
public class NonRetryableProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NonRetryableProviderException(String message) {
        super(message);
    }

    public NonRetryableProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
