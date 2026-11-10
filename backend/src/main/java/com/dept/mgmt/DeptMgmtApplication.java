package com.dept.mgmt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 基于AI的部门工作管理软件 - 后端启动类
 *
 * @author dept-mgmt
 */
@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
@MapperScan("com.dept.mgmt.mapper")
public class DeptMgmtApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeptMgmtApplication.class, args);
        System.out.println("""

                ====================================================
                  基于AI的部门工作管理软件 后端服务启动成功
                  接口地址: http://localhost:8080/api
                  接口文档: http://localhost:8080/api/swagger-ui.html
                ====================================================
                """);
    }
}
