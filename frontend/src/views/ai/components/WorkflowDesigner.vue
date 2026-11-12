<template>
  <div class="wf-designer" :class="{ readonly }">
    <!-- 工具条 -->
    <div class="designer-toolbar" v-if="!readonly">
      <el-button size="small" @click="addNode('start')" :disabled="hasStart">+ 开始</el-button>
      <el-button size="small" @click="addNode('agent')">+ 能力执行</el-button>
      <el-button size="small" @click="addNode('approval')">+ 人工审批</el-button>
      <el-button size="small" @click="addNode('notification')">+ 结果通知</el-button>
      <el-divider direction="vertical" />
      <el-button size="small" :disabled="!selected" @click="removeSelected">
        删除选中{{ selectedType === 'edge' ? '连线' : '节点' }}
      </el-button>
      <span class="designer-tip">拖拽节点调整位置 · 从节点右缘圆点拖出连线 · 点击节点配置参数</span>
    </div>
    <div class="designer-toolbar" v-else>
      <slot name="instance-toolbar"></slot>
    </div>

    <!-- 画布 -->
    <div class="designer-canvas" ref="canvasRef" @pointerdown.self="clearSelect">
      <div class="canvas-inner" :style="{ width: canvasW + 'px', height: canvasH + 'px' }">
        <!-- 连线层 -->
        <svg class="edge-layer" :width="canvasW" :height="canvasH">
          <path v-for="e in edges" :key="e.from + '->' + e.to"
                :d="edgePath(e.from, e.to)"
                class="edge-path"
                :class="{ selected: selectedType === 'edge' && selected === e.from + '->' + e.to }"
                @pointerdown.stop="selectEdge(e)" />
          <!-- 拖拽中的临时连线 -->
          <path v-if="connecting" :d="tempPath" class="edge-path temp" />
        </svg>

        <!-- 节点层 -->
        <div v-for="n in nodes" :key="n.key"
             class="wf-node"
             :class="[nodeClass(n), { selected: selectedType === 'node' && selected === n.key, connecting: connecting?.from === n.key }]"
             :style="{ left: n.x + 'px', top: n.y + 'px' }"
             @pointerdown.stop="onNodePointerDown(n, $event)"
             @pointerup.stop="onNodePointerUp(n)"
             @click.stop="readonly && emit('node-click', n.key)">
          <div class="node-type-dot" :style="{ background: TYPE_COLOR[n.type] }"></div>
          <div class="node-body">
            <div class="node-name">{{ n.name }}</div>
            <div class="node-type-label">{{ TYPE_LABEL[n.type] }}<template v-if="n.type === 'agent' && n.config?.capabilityCode"> · {{ n.config.capabilityCode }}</template></div>
          </div>
          <!-- 入端口指示 / 出端口连线热点 -->
          <div class="port in"></div>
          <div v-if="!readonly" class="port out"
               @pointerdown.stop.prevent="startConnect(n, $event)"
               title="从此处拖出连线"></div>
          <span v-if="!readonly" class="node-del" @pointerdown.stop @click.stop="removeNode(n.key)">×</span>
          <!-- 实例态角标 -->
          <span v-if="readonly && nodeState(n.key)" class="node-state-tag" :style="{ color: STATE_COLOR[nodeState(n.key)] }">
            {{ STATE_LABEL[nodeState(n.key)] || nodeState(n.key) }}
          </span>
        </div>
      </div>
    </div>

    <!-- 节点配置抽屉 -->
    <el-drawer v-model="configVisible" :title="`节点配置 · ${TYPE_LABEL[editing?.type || ''] || ''}`" size="380px" :close-on-click-modal="true">
      <el-form v-if="editing" label-width="88px" label-position="left">
        <el-form-item label="节点名称"><el-input v-model="editing.name" maxlength="30" /></el-form-item>

        <template v-if="editing.type === 'agent'">
          <el-form-item label="能力点">
            <el-select v-model="editing.config!.capabilityCode" filterable placeholder="选择 Agent 能力" style="width: 100%">
              <el-option v-for="p in capabilities" :key="p.capabilityCode" :label="`${p.capabilityCode} ${p.capabilityName || ''}`" :value="p.capabilityCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行指令">
            <el-input v-model="editing.config!.instruction" type="textarea" :rows="4" maxlength="500" show-word-limit
                      placeholder="任务指令，如：projectId=1 齐套检查" />
          </el-form-item>
        </template>

        <template v-else-if="editing.type === 'approval'">
          <el-form-item label="审批人">
            <el-select v-model="editing.config!.assignee" style="width: 100%">
              <el-option label="流程发起人" value="starter" />
              <el-option v-for="u in users" :key="u.id" :label="u.name || u.username" :value="String(u.id)" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="editing.type === 'notification'">
          <el-form-item label="通知对象">
            <el-select v-model="editing.config!.target" style="width: 100%">
              <el-option label="流程发起人" value="starter" />
              <el-option v-for="u in users" :key="u.id" :label="u.name || u.username" :value="String(u.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知标题"><el-input v-model="editing.config!.title" maxlength="50" /></el-form-item>
          <el-form-item label="通知内容">
            <el-input v-model="editing.config!.content" type="textarea" :rows="3" maxlength="200" />
          </el-form-item>
        </template>

        <div class="drawer-tip" v-if="editing.type === 'start'">开始节点为流程入口，无需配置参数。</div>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAutonomyPoliciesApi } from '@/api/agent'
import { getUserOptionsApi } from '@/api/user'
import type { WfEdge, WfGraph, WfNode } from '@/api/aiws'

const props = defineProps<{
  graph: WfGraph
  readonly?: boolean
  /** 实例态节点状态映射 nodeKey -> status */
  nodeStates?: Record<string, string>
}>()

const emit = defineEmits<{ (e: 'change', graph: WfGraph): void; (e: 'node-click', nodeKey: string): void }>()

const NODE_W = 156
const NODE_H = 54
const canvasW = 1100
const canvasH = 620

const TYPE_LABEL: Record<string, string> = { start: '开始', agent: '能力执行', approval: '人工审批', notification: '结果通知' }
const TYPE_COLOR: Record<string, string> = { start: '#909399', agent: '#409eff', approval: '#e6a23c', notification: '#67c23a' }
const STATE_LABEL: Record<string, string> = {
  pending: '待执行', running: '执行中', waiting_approval: '待审批', done: '已完成', failed: '失败', skipped: '已跳过'
}
const STATE_COLOR: Record<string, string> = {
  pending: '#909399', running: '#409eff', waiting_approval: '#e6a23c', done: '#67c23a', failed: '#f56c6c', skipped: '#c0c4cc'
}

const canvasRef = ref<HTMLElement>()
const nodes = ref<WfNode[]>([])
const edges = ref<WfEdge[]>([])
const selected = ref('')
const selectedType = ref<'node' | 'edge' | ''>('')
const configVisible = ref(false)
const editing = ref<WfNode | null>(null)
const capabilities = ref<{ capabilityCode: string; capabilityName?: string }[]>([])
const users = ref<{ id: number; username: string; name?: string }[]>([])

/** 连线进行态 */
const connecting = ref<{ from: string; x: number; y: number } | null>(null)
/** 节点拖拽进行态 */
let dragState: { key: string; dx: number; dy: number } | null = null
/** 指针按下位置（区分点击与拖拽：位移 <4px 视为点击打开配置） */
let downPoint = { x: 0, y: 0 }
let moved = false

const hasStart = computed(() => nodes.value.some(n => n.type === 'start'))

function nodeClass(n: WfNode) {
  if (!props.readonly) return 'type-' + n.type
  const st = nodeState(n.key)
  return 'state-' + (st || 'pending')
}

function nodeState(key: string) {
  return props.nodeStates?.[key] || ''
}

// ==================== 编辑操作 ====================

function addNode(type: WfNode['type']) {
  if (type === 'start' && hasStart.value) return
  const key = 'n' + (nodes.value.length + 1) + '_' + Math.random().toString(36).slice(2, 5)
  nodes.value.push({
    key, name: TYPE_LABEL[type], type,
    x: 120 + Math.round(Math.random() * 60), y: 90 + Math.round(Math.random() * 200),
    config: type === 'start' ? {} : { capabilityCode: '', instruction: '', assignee: 'starter', target: 'starter', title: '', content: '' }
  })
  changed()
}

function removeNode(key: string) {
  nodes.value = nodes.value.filter(n => n.key !== key)
  edges.value = edges.value.filter(e => e.from !== key && e.to !== key)
  if (selected.value === key) clearSelect()
  changed()
}

function selectEdge(e: WfEdge) {
  selected.value = e.from + '->' + e.to
  selectedType.value = 'edge'
}

function removeSelected() {
  if (selectedType.value === 'edge' && selected.value) {
    const [from, to] = selected.value.split('->')
    edges.value = edges.value.filter(e => !(e.from === from && e.to === to))
  } else if (selectedType.value === 'node' && selected.value) {
    removeNode(selected.value)
    return
  }
  clearSelect()
  changed()
}

function clearSelect() {
  selected.value = ''
  selectedType.value = ''
  configVisible.value = false
}

function openConfig(n: WfNode) {
  selected.value = n.key
  selectedType.value = 'node'
  editing.value = n
  configVisible.value = true
}

function changed() {
  emit('change', { nodes: nodes.value, edges: edges.value })
}

// ==================== 指针交互：拖拽节点 / 拖拽连线 ====================

function canvasPoint(ev: PointerEvent): { x: number; y: number } {
  const rect = canvasRef.value?.getBoundingClientRect()
  return { x: ev.clientX - (rect?.left || 0) + (canvasRef.value?.scrollLeft || 0),
           y: ev.clientY - (rect?.top || 0) + (canvasRef.value?.scrollTop || 0) }
}

function onNodePointerDown(n: WfNode, ev: PointerEvent) {
  if (props.readonly) return
  const p = canvasPoint(ev)
  downPoint = { x: ev.clientX, y: ev.clientY }
  moved = false
  dragState = { key: n.key, dx: p.x - n.x, dy: p.y - n.y }
  selected.value = n.key
  selectedType.value = 'node'
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function startConnect(n: WfNode, ev: PointerEvent) {
  const p = canvasPoint(ev)
  connecting.value = { from: n.key, x: p.x, y: p.y }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onNodePointerUp(n: WfNode) {
  if (connecting.value && connecting.value.from !== n.key) {
    const dup = edges.value.some(e => e.from === connecting.value!.from && e.to === n.key)
    if (!dup) {
      edges.value.push({ from: connecting.value.from, to: n.key })
      changed()
    }
  }
}

function onPointerMove(ev: PointerEvent) {
  if (Math.abs(ev.clientX - downPoint.x) + Math.abs(ev.clientY - downPoint.y) > 4) {
    moved = true
  }
  const p = canvasPoint(ev)
  if (dragState) {
    const n = nodes.value.find(x => x.key === dragState!.key)
    if (n) {
      n.x = Math.max(0, Math.min(canvasW - NODE_W, p.x - dragState.dx))
      n.y = Math.max(0, Math.min(canvasH - NODE_H, p.y - dragState.dy))
    }
  } else if (connecting.value) {
    connecting.value.x = p.x
    connecting.value.y = p.y
  }
}

function onPointerUp() {
  if (dragState) {
    if (!moved) {
      // 未发生拖拽位移 → 视为点击，打开节点配置
      const n = nodes.value.find(x => x.key === dragState!.key)
      if (n) openConfig(n)
    } else {
      changed()
    }
    dragState = null
  }
  connecting.value = null
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

// ==================== 几何计算 ====================

function nodeCenter(key: string): { x: number; y: number } | null {
  const n = nodes.value.find(x => x.key === key)
  return n ? { x: n.x + NODE_W / 2, y: n.y + NODE_H / 2 } : null
}

function edgePath(from: string, to: string): string {
  const f = nodeCenter(from)
  const t = nodeCenter(to)
  if (!f || !t) return ''
  const x1 = f.x + NODE_W / 2
  const y1 = f.y
  const x2 = t.x - NODE_W / 2
  const y2 = t.y
  const dx = Math.max(40, Math.abs(x2 - x1) / 2)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}

const tempPath = computed(() => {
  if (!connecting.value) return ''
  const f = nodeCenter(connecting.value.from)
  if (!f) return ''
  const x1 = f.x + NODE_W / 2
  const y1 = f.y
  const x2 = connecting.value.x
  const y2 = connecting.value.y
  const dx = Math.max(30, Math.abs(x2 - x1) / 2)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
})

// ==================== 初始化 ====================

onMounted(async () => {
  nodes.value = props.graph?.nodes ? JSON.parse(JSON.stringify(props.graph.nodes)) : []
  edges.value = props.graph?.edges ? JSON.parse(JSON.stringify(props.graph.edges)) : []
  try {
    const res = await getAutonomyPoliciesApi()
    capabilities.value = (res.data || []).map((p: any) => ({ capabilityCode: p.capabilityCode, capabilityName: p.capabilityName }))
  } catch { /* 能力列表加载失败不阻塞设计器 */ }
  try {
    const res = await getUserOptionsApi()
    users.value = res.data || []
  } catch { /* 用户列表加载失败不阻塞 */ }
})

defineExpose({ openConfig })
</script>

<style scoped>
.wf-designer { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.designer-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 4px; flex-wrap: wrap; }
.designer-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.designer-canvas {
  flex: 1; min-height: 0; overflow: auto; position: relative;
  background:
    linear-gradient(90deg, var(--el-fill-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(var(--el-fill-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px,
    var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter); border-radius: 10px;
}
.canvas-inner { position: relative; }
.edge-layer { position: absolute; left: 0; top: 0; pointer-events: none; }
.edge-path {
  fill: none; stroke: var(--el-border-color-dark); stroke-width: 2;
  pointer-events: stroke; cursor: pointer;
}
.edge-path.selected { stroke: var(--el-color-primary); stroke-width: 2.5; }
.edge-path.temp { stroke: var(--el-color-primary-light-5); stroke-dasharray: 6 4; pointer-events: none; }

.wf-node {
  position: absolute; width: 156px; height: 54px;
  background: var(--el-bg-color); border: 1.5px solid var(--el-border-color);
  border-radius: 10px; display: flex; align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: grab; user-select: none; transition: border-color 0.15s, box-shadow 0.15s;
}
.wf-node:active { cursor: grabbing; }
.wf-node.selected { border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.wf-node.connecting { border-color: var(--el-color-primary-light-5); }
.type-start { border-color: var(--el-color-info-light-5); }
.type-agent { border-color: var(--el-color-primary-light-5); }
.type-approval { border-color: var(--el-color-warning-light-5); }
.type-notification { border-color: var(--el-color-success-light-5); }

/* 实例态着色 */
.state-done { border-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.state-running { border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.state-waiting_approval { border-color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.state-failed { border-color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.state-skipped { opacity: 0.55; }

.node-type-dot { width: 8px; height: 34px; border-radius: 4px; margin: 0 10px; flex-shrink: 0; }
.node-body { min-width: 0; }
.node-name { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 108px; }
.node-type-label { font-size: 11px; color: var(--el-text-color-secondary); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 108px; }

.port {
  position: absolute; width: 10px; height: 10px; border-radius: 50%;
  background: var(--el-bg-color); border: 2px solid var(--el-border-color-dark);
}
.port.in { left: -5px; top: 50%; transform: translateY(-50%); }
.port.out { right: -5px; top: 50%; transform: translateY(-50%); cursor: crosshair; }
.port.out:hover { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }

.node-del {
  position: absolute; top: -8px; right: -8px; width: 18px; height: 18px;
  border-radius: 50%; background: var(--el-color-danger); color: #fff;
  font-size: 12px; line-height: 18px; text-align: center; cursor: pointer; display: none;
}
.wf-node:hover .node-del, .wf-node.selected .node-del { display: block; }

.node-state-tag {
  position: absolute; top: -9px; right: 8px; font-size: 11px; font-weight: 600;
  background: var(--el-bg-color); border-radius: 8px; padding: 0 6px;
  border: 1px solid currentColor;
}
.drawer-tip { font-size: 12px; color: var(--el-text-color-secondary); padding: 8px 0; }
</style>
