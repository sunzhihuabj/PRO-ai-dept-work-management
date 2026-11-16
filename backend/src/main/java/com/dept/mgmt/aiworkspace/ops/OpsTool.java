package com.dept.mgmt.aiworkspace.ops;

import com.dept.mgmt.security.LoginUser;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;


/**
 * AI 系统操作工具定义（T-411）
 *
 * <p>每个工具对应系统一个版块操作：查询类同步执行；写入类经用户确认后执行。
 * executor 复用现有 Service 层方法，不新增 SQL；结果统一由渲染器格式化为
 * 贴合系统数据规范的 Markdown（字段中文名/状态中文/单号）。
 *
 * @author dept-mgmt
 */
@Data
@Builder
public class OpsTool {

    /** 工具名（版块.动作，LLM 选择用） */
    private String name;

    /** 版块中文名 */
    private String module;

    /** 操作标题 */
    private String title;

    /** 一句话说明（注入 LLM 工具目录） */
    private String desc;

    /** 是否只读（只读同步执行；写入需用户确认） */
    private boolean readOnly;

    /** 参数声明 */
    private List<OpsParam> params;

    /** 所需角色（如 ROLE_DEPT_LEADER；空=登录即可） */
    private String minRole;

    /** 所需精细权限点（如 api:contract:link；空=不校验） */
    private String permCode;

    /** 执行器：params（已校验转换）+ 当前用户 → 结果（Map/List/实体/PageResult） */
    private BiFunction<Map<String, Object>, LoginUser, Object> executor;

    /** 结果展示列（字段名→中文列头，LinkedHashMap 保序） */
    private Map<String, String> displayCols;

    /** 结果渲染器：执行结果 + 参数 → Markdown 文本；null 用通用渲染 */
    private BiFunction<Object, Map<String, Object>, String> renderer;

    /**
     * 写入前置校验（P0-4）：执行前先查目标实体是否存在（如任务ID/合同ID），
     * 返回 null=通过；返回文案=拦截提示。挂载在确认执行与参数校验之后。
     */
    private BiFunction<Map<String, Object>, LoginUser, String> precheck;
}
