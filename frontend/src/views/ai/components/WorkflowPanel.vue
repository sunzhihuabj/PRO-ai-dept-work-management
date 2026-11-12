<template>
  <div class="wf-panel">
    <!-- ==================== 视图：流程列表 ==================== -->
    <template v-if="view === 'list'">
      <div class="panel-toolbar">
        <el-button type="primary" @click="newWorkflow">新建流程</el-button>
        <el-radio-group v-model="listScope" size="small" @change="loadList">
          <el-radio-button value="all">可用流程</el-radio-button>
          <el-radio-button value="mine">我创建的</el-radio-button>
        </el-radio-group>
        <el-button size="small" plain @click="showInstances = !showInstances">
          {{ showInstances ? '隐藏运行实例' : '查看运行实例' }}
        </el-button>
      </div>

      <!-- 运行实例列表 -->
      <template v-if="showInstances">
        <div class="section-title">运行实例</div>
        <el-table :data="instances" size="small" border v-loading="instLoading">
          <el-table-column prop="instanceNo" label="实例号" width="160" />
          <el-table-column prop="workflowName" label="流程" min-width="140" show-overflow-tooltip />
          <el-table-column prop="startedBy" label="发起人" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="INST_STATUS[row.status]" size="small">{{ INST_LABEL[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openInstance(row.id)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 流程定义列表 -->
      <div class="section-title">流程定义</div>
      <el-table :data="workflows" size="small" border v-loading="loading">
        <el-table-column prop="wfCode" label="编码" width="150" />
        <el-table-column prop="name" label="流程名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="WF_STATUS[row.status]" size="small">{{ WF_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="64" />
        <el-table-column prop="createBy" label="创建人" width="96" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'published'" link type="primary" size="small" @click="editWorkflow(row)">编辑</el-button>
            <el-button v-if="row.status !== 'published'" link type="success" size="small" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'published'" link type="warning" size="small" @click="offline(row)">下线</el-button>
            <el-button v-if="row.status === 'published'" link type="primary" size="small" @click="start(row)">启动</el-button>
            <el-button v-if="row.status !== 'published'" link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ==================== 视图：设计器（新建/编辑） ==================== -->
    <template v-else-if="view === 'design'">
      <div class="panel-toolbar">
        <el-button size="small" @click="view = 'list'">← 返回列表</el-button>
        <el-input v-model="editingName" placeholder="流程名称" style="width: 220px" maxlength="50" />
        <el-input v-model="editingDesc" placeholder="流程说明（可选）" style="width: 280px" maxlength="200" />
        <el-button size="small" @click="saveDraft" :loading="saving">保存草稿</el-button>
        <el-button size="small" type="primary" @click="saveAndPublish" :loading="saving">保存并发布</el-button>
      </div>
      <div class="designer-wrap">
        <WorkflowDesigner ref="designerRef" :graph="editingGraph" @change="onGraphChange" />
      </div>
    </template>

    <!-- ==================== 视图：实例详情（只读画布 + 运行信息） ==================== -->
    <template v-else-if="view === 'instance'">
      <div class="panel-toolbar">
        <el-button size="small" @click="view = 'list'">← 返回列表</el-button>
        <span class="instance-title">{{ instance?.workflowName }} · {{ instance?.instanceNo }}</span>
        <el-tag :type="INST_STATUS[instance?.status || '']" size="small">{{ INST_LABEL[instance?.status || ''] }}</el-tag>
        <el-button v-if="instance?.status === 'running' || instance?.status === 'failed'"
                   size="small" type="danger" plain @click="cancelInstance">取消实例</el-button>
        <el-button size="small" @click="refreshInstance" :loading="instLoading">刷新</el-button>
      </div>
      <div class="designer-wrap">
        <WorkflowDesigner :graph="instanceGraph" readonly :node-states="nodeStateMap" @node-click="onNodeClick" />
      </div>
      <!-- 节点运行/审批操作 -->
      <div class="instance-ops" v-if="selectedRunNode">
        <el-card shadow="never" class="ops-card">
          <div class="ops-head">
            <b>{{ selectedRunNode.nodeName }}</b>
            <el-tag size="small" :type="NODE_ST[selectedRunNode.status]">
              {{ NODE_SL[selectedRunNode.status] || selectedRunNode.status }}
            </el-tag>
          </div>
          <div class="ops-meta" v-if="selectedRunNode.errorMsg">
            <el-text type="danger" size="small">失败原因：{{ selectedRunNode.errorMsg }}</el-text>
          </div>
          <div class="ops-meta" v-if="selectedRunNode.approvalComment">
            <el-text size="small">审批意见：{{ selectedRunNode.approvalComment }}</el-text>
          </div>
          <div class="ops-meta" v-if="selectedRunNode.agentTaskId">
            <el-text size="small">关联 Agent 任务：#{{ selectedRunNode.agentTaskId }}</el-text>
          </div>
          <div class="ops-actions">
            <template v-if="selectedRunNode.status === 'waiting_approval'">
              <el-input v-model="approveComment" size="small" placeholder="审批意见（可选）" style="width: 220px" />
              <el-button size="small" type="success" :loading="acting" @click="doApprove(true)">通过</el-button>
              <el-button size="small" type="danger" :loading="acting" @click="doApprove(false)">驳回</el-button>
            </template>
            <el-button v-if="selectedRunNode.status === 'failed'" size="small" type="warning"
                       :loading="acting" @click="doRetry">重试该节点</el-button>
          </div>
        </el-card>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkflowDesigner from './WorkflowDesigner.vue'
import {
  listWorkflowsApi, getWorkflowApi, createWorkflowApi, updateWorkflowApi, deleteWorkflowApi,
  publishWorkflowApi, offlineWorkflowApi, startInstanceApi,
  listInstancesApi, getInstanceApi, approveNodeApi, retryNodeApi, cancelInstanceApi,
  type WfWorkflow, type WfInstance, type WfInstanceNode, type WfGraph
} from '@/api/aiws'

type View = 'list' | 'design' | 'instance'

const view = ref<View>('list')
const listScope = ref<'all' | 'mine'>('all')
const workflows = ref<WfWorkflow[]>([])
const instances = ref<WfInstance[]>([])
const loading = ref(false)
const instLoading = ref(false)
const showInstances = ref(false)

// 编辑态
const editingId = ref<number | null>(null)
const editingName = ref('')
const editingDesc = ref('')
const editingGraph = ref<WfGraph>(EMPTY_GRAPH())
const dirtyGraph = ref('')
const saving = ref(false)

// 实例详情态
const instance = ref<WfInstance | null>(null)
const instanceNodes = ref<WfInstanceNode[]>([])
const selectedRunNode = ref<WfInstanceNode | null>(null)
const approveComment = ref('')
const acting = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null

const WF_LABEL: Record<string, string> = { draft: '草稿', published: '已发布', offline: '已下线' }
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const WF_STATUS: Record<string, TagType> = { draft: 'info', published: 'success', offline: 'warning' }
const INST_LABEL: Record<string, string> = { running: '运行中', completed: '已完成', failed: '失败', cancelled: '已取消' }
const INST_STATUS: Record<string, TagType> = { running: 'primary', completed: 'success', failed: 'danger', cancelled: 'info' }
const NODE_SL: Record<string, string> = {
  pending: '待执行', running: '执行中', waiting_approval: '待审批', done: '已完成', failed: '失败', skipped: '已跳过'
}
const NODE_ST: Record<string, TagType> = {
  pending: 'info', running: 'primary', waiting_approval: 'warning', done: 'success', failed: 'danger', skipped: 'info'
}

function EMPTY_GRAPH(): WfGraph {
  return {
    nodes: [{ key: 'start', name: '开始', type: 'start', x: 60, y: 260, config: {} }],
    edges: []
  }
}

// ==================== 列表 ====================

async function loadList() {
  loading.value = true
  try {
    const res = await listWorkflowsApi(listScope.value === 'mine')
    workflows.value = res.data || []
    if (showInstances.value) await loadInstances()
  } finally {
    loading.value = false
  }
}

async function loadInstances() {
  instLoading.value = true
  try {
    const res = await listInstancesApi()
    instances.value = res.data || []
  } finally {
    instLoading.value = false
  }
}

// ==================== 设计器 ====================

function newWorkflow() {
  editingId.value = null
  editingName.value = ''
  editingDesc.value = ''
  editingGraph.value = EMPTY_GRAPH()
  dirtyGraph.value = JSON.stringify(editingGraph.value)
  view.value = 'design'
}

async function editWorkflow(row: any) {
  const res = await getWorkflowApi(row.id)
  const wf = res.data
  editingId.value = wf.id
  editingName.value = wf.name
  editingDesc.value = wf.description || ''
  editingGraph.value = parseGraph(wf.graph)
  dirtyGraph.value = wf.graph || ''
  view.value = 'design'
}

function onGraphChange(g: WfGraph) {
  dirtyGraph.value = JSON.stringify({ nodes: g.nodes, edges: g.edges })
}

async function saveDraft() {
  if (!editingName.value.trim()) {
    ElMessage.warning('请填写流程名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateWorkflowApi(editingId.value, { name: editingName.value, description: editingDesc.value, graph: dirtyGraph.value })
      ElMessage.success('草稿已保存')
    } else {
      const res = await createWorkflowApi({ name: editingName.value, description: editingDesc.value, graph: dirtyGraph.value })
      editingId.value = res.data?.id ?? null
      ElMessage.success('草稿已创建')
    }
    view.value = 'list'
    loadList()
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  if (!editingName.value.trim()) {
    ElMessage.warning('请填写流程名称')
    return
  }
  saving.value = true
  try {
    let id = editingId.value
    if (id) {
      await updateWorkflowApi(id, { name: editingName.value, description: editingDesc.value, graph: dirtyGraph.value })
    } else {
      const res = await createWorkflowApi({ name: editingName.value, description: editingDesc.value, graph: dirtyGraph.value })
      id = res.data?.id ?? null
    }
    if (id) {
      await publishWorkflowApi(id)
      ElMessage.success('已保存并发布')
    }
    view.value = 'list'
    loadList()
  } finally {
    saving.value = false
  }
}

async function publish(row: any) {
  await publishWorkflowApi(row.id)
  ElMessage.success(`流程 ${row.name} 已发布`)
  loadList()
}

async function offline(row: any) {
  await offlineWorkflowApi(row.id)
  ElMessage.success(`流程 ${row.name} 已下线`)
  loadList()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除流程「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteWorkflowApi(row.id)
  ElMessage.success('已删除')
  loadList()
}

async function start(row: any) {
  const res = await startInstanceApi(row.id)
  ElMessage.success(`实例 ${res.data?.instanceNo} 已启动`)
  showInstances.value = true
  if (res.data?.id) {
    await openInstance(res.data.id)
  } else {
    loadList()
  }
}

// ==================== 实例详情 ====================

function parseGraph(json?: string): WfGraph {
  try {
    const g = json ? JSON.parse(json) : null
    return g && g.nodes ? g : EMPTY_GRAPH()
  } catch {
    return EMPTY_GRAPH()
  }
}

const instanceGraph = computed(() => parseGraph(instance.value?.graphSnapshot))

const nodeStateMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  instanceNodes.value.forEach(n => { map[n.nodeKey] = n.status })
  return map
})

async function openInstance(id: number) {
  view.value = 'instance'
  selectedRunNode.value = null
  await refreshInstance()
  schedulePoll()
}

async function refreshInstance() {
  if (!instance.value && view.value !== 'instance') return
  instLoading.value = true
  try {
    const id = instance.value?.id
    if (!id) return
    const res = await getInstanceApi(id)
    instance.value = res.data?.instance || null
    instanceNodes.value = res.data?.nodes || []
    if (selectedRunNode.value) {
      selectedRunNode.value = instanceNodes.value.find(n => n.nodeKey === selectedRunNode.value?.nodeKey) || null
    }
    schedulePoll()
  } finally {
    instLoading.value = false
  }
}

function schedulePoll() {
  if (pollTimer) clearTimeout(pollTimer)
  if (instance.value?.status === 'running') {
    pollTimer = setTimeout(refreshInstance, 5000)
  }
}

function onNodeClick(nodeKey: string) {
  selectedRunNode.value = instanceNodes.value.find(n => n.nodeKey === nodeKey) || null
  approveComment.value = ''
}

async function doApprove(pass: boolean) {
  if (!instance.value || !selectedRunNode.value) return
  acting.value = true
  try {
    await approveNodeApi(instance.value.id, selectedRunNode.value.nodeKey, pass, approveComment.value || undefined)
    ElMessage.success(pass ? '已通过' : '已驳回')
    selectedRunNode.value = null
    refreshInstance()
  } finally {
    acting.value = false
  }
}

async function doRetry() {
  if (!instance.value || !selectedRunNode.value) return
  acting.value = true
  try {
    await retryNodeApi(instance.value.id, selectedRunNode.value.nodeKey)
    ElMessage.success('已重新发起该节点')
    refreshInstance()
  } finally {
    acting.value = false
  }
}

async function cancelInstance() {
  if (!instance.value) return
  await ElMessageBox.confirm('确认取消该流程实例？未完成节点将全部跳过。', '取消确认', { type: 'warning' })
  await cancelInstanceApi(instance.value.id)
  ElMessage.success('实例已取消')
  refreshInstance()
}

onMounted(loadList)
onUnmounted(() => { if (pollTimer) clearTimeout(pollTimer) })

defineExpose({ openWorkflowList: () => { view.value = 'list'; loadList() } })
</script>

<style scoped>
.wf-panel { display: flex; flex-direction: column; height: 100%; min-height: 0; gap: 10px; overflow: auto; }
.panel-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.section-title { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); margin: 4px 0; }
.designer-wrap { flex: 1; min-height: 420px; }
.instance-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.instance-ops { flex-shrink: 0; }
.ops-card { max-width: 640px; }
.ops-head { display: flex; align-items: center; gap: 8px; }
.ops-meta { margin-top: 6px; }
.ops-actions { margin-top: 10px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
