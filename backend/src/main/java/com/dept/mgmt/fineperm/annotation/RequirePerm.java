package com.dept.mgmt.fineperm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级精细权限校验注解（T-404）
 * 标注在 Controller 方法上，由 FinePermInterceptor 校验当前用户是否持有对应 API 权限点。
 * 灰度原则：用户三维度均未配置任何精细权限时不受影响（未配置=不限制）。
 *
 * @author dept-mgmt
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** API 权限点编码，如 api:system/user:list */
    String value();
}
