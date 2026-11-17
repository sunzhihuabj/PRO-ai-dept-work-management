<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { studioConfig } from '../api/hermes'
import { useRuntimeStore } from '../stores/runtime'

const cfg = ref<any>(null)
const runtime = useRuntimeStore()

onMounted(async () => {
  cfg.value = await studioConfig().catch(() => null)
})
</script>

<template>
  <div>
    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Connection /></el-icon>连接拓扑</div>
      <pre class="topo">浏览器 → Hermes Studio BFF (Express :{{ cfg?.port || 8650 }}，登录鉴权 + 工作流存储 + 静态托管)
        → 透传代理（SSE 直通）→ Hermes API Server :8642 ({{ cfg?.hermesBaseUrl }})
Hermes Agent → 模型：部门 ai-gateway :8090（OpenAI 兼容）
Hermes Agent → 工具：部门 backend :8081 /api/mcp（MCP JSON-RPC + 审批化写工具）</pre>
    </div>

    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Key /></el-icon>服务端配置（server/.env）</div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="Hermes 上游">{{ cfg?.hermesBaseUrl }}</el-descriptions-item>
        <el-descriptions-item label="API Key（脱敏）">{{ cfg?.hermesKeyMasked }}</el-descriptions-item>
        <el-descriptions-item label="Studio 版本">v{{ cfg?.version }}</el-descriptions-item>
        <el-descriptions-item label="当前用户">{{ cfg?.user }}</el-descriptions-item>
        <el-descriptions-item label="登录账号/密码">
          <span class="muted">STUDIO_USER / STUDIO_PASS（.env），修改后重启 BFF 生效</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="hs-card">
      <div class="hs-card-title"><el-icon><InfoFilled /></el-icon>运行要点（来自源码级调研）</div>
      <ul class="tips">
        <li>API_SERVER_KEY 必填且 ≥16 字符，Hermes 网关否则拒绝启动（fail-closed）</li>
        <li>/v1/runs 终态集合：completed / failed / cancelled / interrupted；状态保留 1 小时，SSE backlog 1000 条</li>
        <li>Idempotency-Key 幂等保留 24h：同 key 同体 202 重放原 run，同 key 异体 409</li>
        <li>审批 choices：once / session / always / deny；审批超时 300s（approvals.timeout）</li>
        <li>CORS 白名单 API_SERVER_CORS_ORIGINS；本服务走 BFF 代理，浏览器无跨域问题</li>
        <li>多 profile：/p/&lt;profile&gt;/v1/... 镜像路由，各 profile 独立 KEY/会话/凭据</li>
        <li> jobs_admin=false 时定时任务页自动降级（能力面驱动）</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.topo {
  margin: 0;
  background: #f7f8fa;
  padding: 14px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
}
.tips { margin: 0; padding-left: 20px; font-size: 13px; line-height: 2; }
</style>
