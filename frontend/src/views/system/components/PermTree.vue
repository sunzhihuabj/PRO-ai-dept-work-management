<template>
  <div class="perm-tree-wrap">
    <div class="tree-toolbar">
      <el-button size="small" type="success" :disabled="!checkedCodes.length" @click="batch('GRANT')">
        批量授予（{{ checkedCodes.length }}）
      </el-button>
      <el-button size="small" type="warning" :disabled="!checkedCodes.length" @click="batch('DENY')">
        批量拒绝
      </el-button>
      <el-button size="small" type="danger" :disabled="!checkedCodes.length" @click="batchRemove">
        批量移除
      </el-button>
      <el-button size="small" plain @click="applyTemplate">应用模板</el-button>
      <el-button size="small" link type="primary" @click="load">刷新</el-button>
      <span class="legend">
        <el-tag size="small" type="success" effect="dark">已授予</el-tag>
        <el-tag size="small" type="danger" effect="dark">已拒绝</el-tag>
      </span>
    </div>

    <el-scrollbar max-height="420px">
      <el-tree
        ref="treeRef"
        v-loading="loading"
        :data="points"
        node-key="permCode"
        show-checkbox
        default-expand-all
        :props="{ label: 'permName', children: 'children' }"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span class="node-name">{{ data.permName }}</span>
            <el-tag size="small" :type="data.permType === 'MENU' ? 'primary' : data.permType === 'BUTTON' ? 'success' : 'info'">
              {{ data.permType === 'MENU' ? '菜单' : data.permType === 'BUTTON' ? '按钮' : '接口' }}
            </el-tag>
            <el-tag v-if="effectMap[data.permCode] === 'GRANT'" size="small" type="success" effect="dark">已授予</el-tag>
            <el-tag v-else-if="effectMap[data.permCode] === 'DENY'" size="small" type="danger" effect="dark">已拒绝</el-tag>
            <span class="node-code">{{ data.permCode }}</span>
          </span>
        </template>
      </el-tree>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPermPointsApi, getAssignmentsApi, assignPermsApi, removePermsApi,
  applyTemplateApi, getTemplatesApi, type PermPointNode, type PermEffect
} from '@/api/finePerm'

const props = defineProps<{
  subjectType: 'DEPT' | 'POSITION'
  subjectId: number
  readonly?: boolean
}>()

const emit = defineEmits<{ (e: 'changed'): void }>()

const treeRef = ref()
const loading = ref(false)
const points = ref<PermPointNode[]>([])
const effectMap = ref<Record<string, string>>({})

const checkedCodes = computed<string[]>(() => {
  // 仅取完全勾选节点；半选节点提示展开到子级
  const keys: string[] = treeRef.value?.getCheckedKeys(false) || []
  return keys
})

async function load() {
  loading.value = true
  try {
    const [pointsRes, assignRes] = await Promise.all([
      getPermPointsApi(),
      getAssignmentsApi(props.subjectType, props.subjectId)
    ])
    points.value = pointsRes.data || []
    const map: Record<string, string> = {}
    for (const a of assignRes.data || []) {
      map[a.permCode] = a.effect
    }
    effectMap.value = map
  } finally {
    loading.value = false
  }
}

watch(() => props.subjectId, () => load(), { immediate: true })
onMounted(load)

async function batch(effect: PermEffect) {
  const codes = checkedCodes.value
  if (!codes.length) {
    ElMessage.warning('请先勾选权限点（半选节点请展开勾选到具体子项）')
    return
  }
  await assignPermsApi({ subjectType: props.subjectType, subjectId: props.subjectId, permCodes: codes, effect })
  ElMessage.success(effect === 'GRANT' ? `已批量授予 ${codes.length} 项，实时生效` : `已批量拒绝 ${codes.length} 项，实时生效`)
  treeRef.value?.setCheckedKeys([])
  await load()
  emit('changed')
}

async function batchRemove() {
  const codes = checkedCodes.value
  if (!codes.length) return
  await ElMessageBox.confirm(`确认移除选中的 ${codes.length} 项权限配置？移除后按其他维度合并规则重新生效`, '确认', { type: 'warning' })
  await removePermsApi({ subjectType: props.subjectType, subjectId: props.subjectId, permCodes: codes, effect: 'GRANT' })
  ElMessage.success('已批量移除，实时生效')
  treeRef.value?.setCheckedKeys([])
  await load()
  emit('changed')
}

async function applyTemplate() {
  const res = await getTemplatesApi()
  const templates = res.data || []
  if (!templates.length) {
    ElMessage.warning('暂无权限模板，请先创建')
    return
  }
  const menu = templates.map(t => `${t.id} = ${t.templateName}`).join('\n')
  try {
    const { value } = await ElMessageBox.prompt(
      `可用模板：\n${menu}\n请输入要应用的模板ID`,
      '应用权限模板',
      { inputPattern: /^\d+$/, inputPlaceholder: '模板ID', inputErrorMessage: '请输入数字ID' }
    )
    const templateId = Number(value)
    if (!templates.some(t => t.id === templateId)) {
      ElMessage.error('模板ID不存在')
      return
    }
    await applyTemplateApi(templateId, props.subjectType, props.subjectId, '界面应用模板')
    ElMessage.success('模板已应用并实时生效')
    await load()
    emit('changed')
  } catch {
    // 用户取消
  }
}

defineExpose({ reload: load })
</script>

<style scoped lang="scss">
.perm-tree-wrap {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px;
}

.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.legend {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.node-name {
  min-width: 80px;
}

.node-code {
  font-family: monospace;
  font-size: 11px;
  color: var(--dm-muted, #767676);
}
</style>
