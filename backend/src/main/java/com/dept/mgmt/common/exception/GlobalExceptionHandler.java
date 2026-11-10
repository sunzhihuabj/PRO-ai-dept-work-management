package com.dept.mgmt.common.exception;

import com.dept.mgmt.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理Controller层抛出的各类异常，转换为标准响应结构
 *
 * @author dept-mgmt
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} | URI: {} | msg: {}", e.getClass().getSimpleName(), request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 - @Valid RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + message);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + message);
    }

    /**
     * 约束校验异常 - @Validated PathVariable/RequestParam
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + message);
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数类型不匹配: " + e.getName());
    }

    /**
     * 认证异常（未登录/Token失效）
     */
    @ExceptionHandler(AuthenticationException.class)
    public R<Void> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        log.warn("认证异常: {} | URI: {}", e.getMessage(), request.getRequestURI());
        return R.fail(HttpStatus.UNAUTHORIZED.value(), "登录已过期，请重新登录");
    }

    /**
     * 权限不足
     */
    @ExceptionHandler(AccessDeniedException.class)
    public R<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("权限不足: {} | URI: {}", e.getMessage(), request.getRequestURI());
        return R.fail(HttpStatus.FORBIDDEN.value(), "无访问权限");
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNotFound(NoHandlerFoundException e) {
        log.warn("资源不存在: {}", e.getRequestURL());
        return R.fail(HttpStatus.NOT_FOUND.value(), "请求资源不存在");
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return R.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    /**
     * 其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} | URI: {}", e.getMessage(), request.getRequestURI(), e);
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误，请稍后重试");
    }
}
