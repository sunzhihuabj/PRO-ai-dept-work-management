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
     * 带结构化参数执行（T-112 扩展）：任务 params JSON 已解析为 Map；
     * 旧实现无需感知参数时不必重写（默认委托两参版本，向后兼容）
     */
    default String execute(String instruction, Long userId, java.util.Map<String, Object> params) throws Exception {
        return execute(instruction, userId);
    }

    /**
     * 是否需要人工确认后才执行（安全优先默认 true：写操作类能力必须显式声明 false 才可自动执行；
     * 是否真正跳过确认由 T-110 自主策略级别共同决定：requireConfirm() && level<2 → 待确认）
     */
    default boolean requireConfirm() {
        return true;
    }
}
