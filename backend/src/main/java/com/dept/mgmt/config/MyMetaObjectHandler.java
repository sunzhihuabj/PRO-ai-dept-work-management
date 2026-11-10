package com.dept.mgmt.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.dept.mgmt.security.LoginUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        try {
            LoginUser loginUser = LoginUser.current();
            if (loginUser != null && metaObject.hasSetter("createBy")) {
                this.strictInsertFill(metaObject, "createBy", String.class, loginUser.getUsername());
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
