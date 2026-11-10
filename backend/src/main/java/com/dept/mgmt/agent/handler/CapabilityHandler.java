package com.dept.mgmt.agent.handler;

/**
 * 能力点执行处理器（T-107）：MasterAgent 按 capability_code 路由到对应实现
 * 新能力上线实现本接口并注册为 Spring Bean 即可被调度
 *
 * @author dept-mgmt
 */
public interface CapabilityHandler {

    /**
     * 支持的能力点编码
     */
    String capabilityCode();

    /**
     * 执行能力
     *
     * @param instruction 用户原始指令
     * @param userId      发起用户（人工语境）或被代理人（ASSISTANT 代理语境）
     * @return 执行结果文本（AI 生成内容由实现方在 result 上自带 [AI] 语义，落库统一打标）
     */
    String execute(String instruction, Long userId) throws Exception;

    /**
     * 是否需要人工确认后才执行（建议级能力返回 true → 任务进入 pending_confirm）
     */
    default boolean requireConfirm() {
        return false;
    }
}
