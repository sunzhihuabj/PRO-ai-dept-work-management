package com.dept.mgmt.aiworkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.aiworkspace.entity.WfWorkflow;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.fineperm.annotation.RequirePerm;
import com.dept.mgmt.mapper.WfWorkflowMapper;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 业务流程定义服务（T-408B）：草稿 CRUD + 图结构校验 + 发布/下线 + 模板生成
 *
 * <p>graph JSON 契约：
 * <pre>{nodes:[{key,name,type,x,y,config:{...}}],edges:[{from,to,label?}]}</pre>
 * 节点类型：start（唯一）/ agent（capabilityCode+instruction）/ approval（assigneeUserId）/ notification（target/title/content）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final java.util.Set<String> NODE_TYPES = java.util.Set.of("start", "agent", "approval", "notification");

    private final WfWorkflowMapper workflowMapper;
    private final CodeEngineService codeEngineService;
    private final ObjectMapper objectMapper;

    /** 创建草稿 */
    @RequirePerm("api:wf:manage")
    public WfWorkflow create(String name, String description, String graph) {
        LoginUser user = current();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "流程名称不能为空");
        }
        validateGraph(graph);
        WfWorkflow wf = new WfWorkflow();
        wf.setWfCode(codeEngineService.nextCode("WF", null));
        wf.setName(name.trim());
        wf.setDescription(description);
        wf.setGraph(graph);
        wf.setStatus("draft");
        wf.setVersion(1);
        wf.setCreateBy(user.getUsername());
        workflowMapper.insert(wf);
        return wf;
    }

    /**
     * 从标准模板生成草稿（T-408A NL 创建流程意图）：
     * 开始 → 能力执行（AGT-13 项目齐套检查示例能力）→ 人工审批（发起人）→ 结果通知（发起人）
     */
    public WfWorkflow createFromTemplate(String name, String username) {
        String graph = """
                {"nodes":[
                  {"key":"start","name":"开始","type":"start","x":60,"y":180,"config":{}},
                  {"key":"agent1","name":"能力执行","type":"agent","x":260,"y":180,"config":{"capabilityCode":"AGT-13","instruction":"执行本项目齐套检查"}},
                  {"key":"approval1","name":"人工审批","type":"approval","x":460,"y":180,"config":{"assignee":"starter"}},
                  {"key":"notify1","name":"结果通知","type":"notification","x":660,"y":180,"config":{"target":"starter","title":"流程已完成","content":"你发起的流程已全部节点完成。"}}
                 ],
                 "edges":[{"from":"start","to":"agent1"},{"from":"agent1","to":"approval1"},{"from":"approval1","to":"notify1"}]}""";
        WfWorkflow wf = new WfWorkflow();
        wf.setWfCode(codeEngineService.nextCode("WF", null));
        wf.setName(name);
        wf.setDescription("由 AI 助手根据自然语言指令生成的标准流程草稿，可在设计器中调整后发布");
        wf.setGraph(graph);
        wf.setStatus("draft");
        wf.setVersion(1);
        wf.setCreateBy(username);
        workflowMapper.insert(wf);
        return wf;
    }

    /** 更新草稿（名称/描述/图；仅 draft/offline 可改） */
    @RequirePerm("api:wf:manage")
    public void update(Long id, String name, String description, String graph) {
        WfWorkflow wf = mustExist(id);
        if (!"draft".equals(wf.getStatus()) && !"offline".equals(wf.getStatus())) {
            throw new BusinessException(400, "已发布流程不可直接编辑，请先下线");
        }
        if (StringUtils.hasText(name)) {
            wf.setName(name.trim());
        }
        if (description != null) {
            wf.setDescription(description);
        }
        if (StringUtils.hasText(graph)) {
            validateGraph(graph);
            wf.setGraph(graph);
        }
        wf.setVersion(wf.getVersion() == null ? 1 : wf.getVersion() + 1);
        workflowMapper.updateById(wf);
    }

    /** 删除（仅 draft/offline；有历史实例也允许删定义，实例含快照不受影响） */
    @RequirePerm("api:wf:manage")
    public void delete(Long id) {
        WfWorkflow wf = mustExist(id);
        if ("published".equals(wf.getStatus())) {
            throw new BusinessException(400, "已发布流程请先下线再删除");
        }
        workflowMapper.deleteById(id);
    }

    /** 发布（校验图连通性：start 可达且无孤立审批缺失） */
    @RequirePerm("api:wf:manage")
    public void publish(Long id) {
        WfWorkflow wf = mustExist(id);
        if (!"draft".equals(wf.getStatus()) && !"offline".equals(wf.getStatus())) {
            throw new BusinessException(400, "流程当前状态不可发布: " + wf.getStatus());
        }
        validateGraph(wf.getGraph());
        wf.setStatus("published");
        workflowMapper.updateById(wf);
        log.info("流程已发布: {} by {}", wf.getWfCode(), LoginUser.current() == null ? "?" : LoginUser.current().getUsername());
    }

    /** 下线（运行中实例不受影响，按启动时快照继续） */
    @RequirePerm("api:wf:manage")
    public void offline(Long id) {
        WfWorkflow wf = mustExist(id);
        wf.setStatus("offline");
        workflowMapper.updateById(wf);
    }

    /** 流程列表（my=true 仅我创建；否则含全部已发布 + 我自己的） */
    public List<WfWorkflow> list(Boolean mine, String status) {
        LambdaQueryWrapper<WfWorkflow> wrapper = new LambdaQueryWrapper<>();
        LoginUser user = current();
        if (Boolean.TRUE.equals(mine)) {
            wrapper.eq(WfWorkflow::getCreateBy, user.getUsername());
        } else {
            wrapper.and(w -> w.eq(WfWorkflow::getStatus, "published")
                    .or().eq(WfWorkflow::getCreateBy, user.getUsername()));
        }
        wrapper.eq(StringUtils.hasText(status), WfWorkflow::getStatus, status);
        wrapper.orderByDesc(WfWorkflow::getUpdateTime);
        return workflowMapper.selectList(wrapper);
    }

    public WfWorkflow detail(Long id) {
        return mustExist(id);
    }

    // ==================== 图结构校验 ====================

    /** 校验图 JSON：节点类型合法、key 唯一、start 唯一、边引用存在、无环（DFS） */
    private void validateGraph(String graph) {
        if (!StringUtils.hasText(graph)) {
            throw new BusinessException(400, "流程图不能为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(graph);
        } catch (Exception e) {
            throw new BusinessException(400, "流程图 JSON 格式非法");
        }
        JsonNode nodes = root.path("nodes");
        JsonNode edges = root.path("edges");
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new BusinessException(400, "流程图缺少节点");
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        int startCount = 0;
        for (JsonNode n : nodes) {
            String key = n.path("key").asText("");
            String type = n.path("type").asText("");
            if (!StringUtils.hasText(key) || !NODE_TYPES.contains(type)) {
                throw new BusinessException(400, "节点 key 缺失或类型非法: " + type);
            }
            if (!keys.add(key)) {
                throw new BusinessException(400, "节点 key 重复: " + key);
            }
            if ("start".equals(type)) {
                startCount++;
            }
            if ("agent".equals(type) && !StringUtils.hasText(n.path("config").path("capabilityCode").asText())) {
                throw new BusinessException(400, "能力节点缺少 capabilityCode: " + key);
            }
        }
        if (startCount != 1) {
            throw new BusinessException(400, "开始节点必须有且仅有一个");
        }
        for (JsonNode e : edges) {
            String from = e.path("from").asText("");
            String to = e.path("to").asText("");
            if (!keys.contains(from) || !keys.contains(to)) {
                throw new BusinessException(400, "连线引用了不存在的节点: " + from + "→" + to);
            }
            if (from.equals(to)) {
                throw new BusinessException(400, "不允许自环连线: " + from);
            }
        }
        if (hasCycle(keys, edges)) {
            throw new BusinessException(400, "流程图存在环路，请调整连线");
        }
    }

    /** DFS 检测有向环 */
    private boolean hasCycle(java.util.Set<String> keys, JsonNode edges) {
        java.util.Map<String, java.util.List<String>> adj = new java.util.HashMap<>();
        for (JsonNode e : edges) {
            adj.computeIfAbsent(e.path("from").asText(), k -> new java.util.ArrayList<>())
                    .add(e.path("to").asText());
        }
        java.util.Map<String, Integer> state = new java.util.HashMap<>();
        for (String k : keys) {
            if (dfs(k, adj, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(String node, java.util.Map<String, java.util.List<String>> adj, java.util.Map<String, Integer> state) {
        int s = state.getOrDefault(node, 0);
        if (s == 1) {
            return true;
        }
        if (s == 2) {
            return false;
        }
        state.put(node, 1);
        for (String next : adj.getOrDefault(node, List.of())) {
            if (dfs(next, adj, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    private WfWorkflow mustExist(Long id) {
        WfWorkflow wf = workflowMapper.selectById(id);
        if (wf == null) {
            throw new BusinessException(404, "流程不存在");
        }
        return wf;
    }

    private LoginUser current() {
        LoginUser user = LoginUser.current();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
