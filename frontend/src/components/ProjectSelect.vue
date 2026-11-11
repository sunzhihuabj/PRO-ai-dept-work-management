<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder || '请选择项目'"
    :clearable="clearable"
    :disabled="disabled"
    :loading="loading"
    filterable
    :filter-method="doFilter"
    no-match-text="未找到匹配的项目，请更换关键词"
    no-data-text="暂无可选项目"
    :style="{ width }"
    @change="onChange"
    @clear="onClear"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="p in visibleProjects"
      :key="p.id"
      :label="p.projectName"
      :value="p.projectName"
    >
      <span class="pj-option">
        <span class="pj-name">{{ p.projectName }}</span>
        <span class="pj-status" :class="p.status === 1 ? 'active' : 'inactive'">
          {{ p.status === 1 ? '进行中' : '已结束' }}
        </span>
      </span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjectListApi, type ProjectItem } from '@/api/project'

/**
 * 统一项目选择组件（T-405 项目名称口径）
 * - v-model 输出值为「项目名称」（projectName），交互与查询传参统一名称口径，不暴露项目ID
 * - 模糊搜索：输入任意字符片段实时匹配；大小写不敏感；相关度排序（前缀命中 > 包含命中，同级保持原序）
 * - 无匹配项展示友好空状态提示
 * - change 事件回传完整项目对象（含 id），供页面内部级联/业务外键使用（如阶段联动）
 */
const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  width?: string
}>(), {
  modelValue: '',
  placeholder: '',
  clearable: true,
  disabled: false,
  width: '100%'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', project: ProjectItem | undefined): void
}>()

const loading = ref(false)
const allProjects = ref<ProjectItem[]>([])
/** 过滤后展示的选项（未输入时=全量） */
const visibleProjects = ref<ProjectItem[]>([])

async function loadProjects() {
  loading.value = true
  try {
    const res = await getProjectListApi({ pageNum: 1, pageSize: 500 })
    allProjects.value = res.data?.records || []
    visibleProjects.value = [...allProjects.value]
  } finally {
    loading.value = false
  }
}

/**
 * 模糊过滤：大小写不敏感；相关度排序——名称前缀命中优先于包含命中，同级保持原始顺序
 */
function doFilter(query: string) {
  const kw = (query || '').trim().toLowerCase()
  if (!kw) {
    visibleProjects.value = [...allProjects.value]
    return
  }
  const startsWith: ProjectItem[] = []
  const includes: ProjectItem[] = []
  for (const p of allProjects.value) {
    const name = (p.projectName || '').toLowerCase()
    if (name.startsWith(kw)) {
      startsWith.push(p)
    } else if (name.includes(kw)) {
      includes.push(p)
    }
  }
  visibleProjects.value = [...startsWith, ...includes]
}

function onChange(value: string) {
  emit('update:modelValue', value)
  const project = value
    ? allProjects.value.find(p => p.projectName === value)
    : undefined
  emit('change', project)
}

function onClear() {
  emit('update:modelValue', '')
  emit('change', undefined)
}

/** 下拉重新展开时恢复全量选项（避免上次过滤残留） */
function onVisibleChange(visible: boolean) {
  if (visible) {
    doFilter('')
  }
}

/** 供父组件强制刷新项目列表（如新建项目后） */
async function reload() {
  await loadProjects()
}

onMounted(loadProjects)

defineExpose({ reload })
</script>

<style scoped>
.pj-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pj-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.pj-status {
  font-size: 12px;
  flex-shrink: 0;
}

.pj-status.active {
  color: var(--el-color-success);
}

.pj-status.inactive {
  color: var(--dm-muted, #909399);
}
</style>
