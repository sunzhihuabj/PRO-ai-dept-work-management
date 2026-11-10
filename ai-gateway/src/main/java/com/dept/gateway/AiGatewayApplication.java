package com.dept.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 网关服务启动类
 * 全系统模型调用唯一出口：Provider 抽象 / 能力路由 / 配额限流 / 出网脱敏 / 调用审计 / 降级链
 *
 * <p>红线：任何业务代码不得直连模型 SDK/URL，必须经本网关。
 *
 * @author dept-mgmt
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.dept.gateway.mapper")
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
