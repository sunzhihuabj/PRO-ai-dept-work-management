package com.dept.mgmt.common.aspect;

import com.dept.mgmt.common.annotation.OperLog;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.SysOperLog;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.SysOperLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：拦截标注 @OperLog 的方法，异步落库；日志失败不影响业务
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperLogService sysOperLogService;
    private final ObjectMapper objectMapper;

    @AfterReturning(pointcut = "@annotation(operLog)", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, OperLog operLog, Object result) {
        save(joinPoint, operLog, null, result);
    }

    @AfterThrowing(pointcut = "@annotation(operLog)", throwing = "ex")
    public void doAfterThrowing(JoinPoint joinPoint, OperLog operLog, Exception ex) {
        save(joinPoint, operLog, ex.getMessage(), null);
    }

    private void save(JoinPoint joinPoint, OperLog operLog, String errorMsg, Object result) {
        try {
            SysOperLog record = new SysOperLog();
            record.setTitle(operLog.title());
            record.setBusinessType(operLog.businessType());
            record.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());
            record.setRequestMethod(httpMethod());
            record.setOperatorType(1);
            LoginUser loginUser = LoginUser.current();
            record.setOperName(loginUser != null ? loginUser.getUsername() : "anonymous");
            record.setOperUrl(requestUrl());
            record.setOperIp(clientIp());
            record.setOperParam(argsJson(joinPoint.getArgs()));
            try {
                record.setJsonResult(result instanceof R ? objectMapper.writeValueAsString(result) : null);
            } catch (Exception ignore) { /* 序列化失败不阻塞 */ }
            record.setStatus(errorMsg == null ? 1 : 0);
            record.setErrorMsg(errorMsg);
            record.setOperTime(LocalDateTime.now());
            sysOperLogService.record(record);
        } catch (Exception e) {
            log.warn("操作日志切面异常: {}", e.getMessage());
        }
    }

    private String argsJson(Object[] args) {
        try {
            List<Object> safe = new ArrayList<>();
            for (Object arg : args) {
                if (arg instanceof MultipartFile || arg instanceof HttpServletRequest) {
                    continue;
                }
                safe.add(arg);
            }
            String json = objectMapper.writeValueAsString(safe);
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String httpMethod() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getMethod() : null;
    }

    private String requestUrl() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getRequestURI() : null;
    }

    private String clientIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
