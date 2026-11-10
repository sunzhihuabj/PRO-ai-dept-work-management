package com.dept.mgmt.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解：标注在 Controller 方法上，自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 操作模块（如：用户管理） */
    String title();

    /** 业务类型：0其它 1新增 2修改 3删除 */
    int businessType() default 0;
}
