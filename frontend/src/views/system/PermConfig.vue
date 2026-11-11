<template>
  <div class="page-container">
    <!-- 顶部说明 -->
    <el-alert type="info" :closable="false" class="intro-alert">
      <template #title>
        多维度权限配置：部门维度、固定职位维度、临时职位维度合并叠加；冲突时
        <b>临时职位 &gt; 固定职位 &gt; 部门维度</b>；变更实时生效（无需重新登录）。
        未配置任何维度的用户不受精细权限约束（灰度原则）。
      </template>
    </el-alert>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ==================== Tab 1：按用户 ==================== -->
        <el-tab-pane label="按用户" name="user">
          <div class="toolbar">
            <el-select v-model="selectedUserId" filterable placeholder="选择用户" class="user-select" @change="onUserChange">
              <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName || u.username}（${u.username}）`" :value="u.id" />
            </el-select>
            <el-button type="primary" :disabled="!selectedUserId" @click="openPreview(selectedUserId)">
              权限预览
            </el-button>
          </div>

          <template v-if="selectedUserId">
            <!-- 职位分配（FIXED/TEMP 维度入口） -->
            <div class="card-header">
              <span class="section-title">职位分配（影响固定/临时职位维度）</span>
              <el-button size="small" type="primary" @click="openAssignPosition">分配职位</el-button>
            </div>
            <el-table :data="userPositions" size="small" border>
              <el-table-column label="职位" prop="positionName" min-width="140" />
              <el-table-column label="性质" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.positionKind === 'TEMP' ? 'warning' : 'success'" size="small">
                    {{ row.positionKind === 'TEMP' ? '临时' : '固定' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="有效期" width="220">
                <template #default="{ row }">
                  <span v-if="row.positionKind === 'TEMP'">{{ formatDate(row.startTime) }} ~ {{ formatDate(row.endTime) }}</span>
                  <span v-else class="dim-text">长期</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.effective ? 'success' : 'info'" size="small">{{ row.effective ? '生效中' : '已失效' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button size="small" type="danger" link @click="onRemoveUserPosition(row as UserPosition)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 所属部门维度快捷配置 -->
            <div class="card-header">
              <span class="section-title">所属部门维度快捷配置</span>
              <span class="dim-text" v-if="selectedUserDept">当前部门：{{ selectedUserDept.deptName }}</span>
            </div>
            <perm-tree
              ref="userDeptTreeRef"
              v-if="selectedUserDept"
              :subject-type="'DEPT'"
              :subject-id="selectedUserDept.id"
              :readonly="false"
            />
          </template>
          <el-empty v-else description="请先选择用户" />
        </el-tab-pane>

        <!-- ==================== Tab 2：按部门 ==================== -->
        <el-tab-pane label="按部门" name="dept">
          <div class="toolbar">
            <el-tree-select
              v-model="selectedDeptId"
              :data="deptTreeData"
              :props="{ label: 'deptName', children: 'children' }"
              node-key="id"
              check-strictly
              filterable
              placeholder="选择部门"
              class="user-select"
              @change="onDeptChange"
            />
            <el-button type="primary" :disabled="!selectedDeptId" @click="openPreviewOfDeptUser">
              预览部门成员权限
            </el-button>
          </div>
          <perm-tree
            ref="deptPermTreeRef"
            v-if="selectedDeptId"
            subject-type="DEPT"
            :subject-id="selectedDeptId"
            @changed="onPermChanged"
          />
          <el-empty v-else description="请先选择部门" />
        </el-tab-pane>

        <!-- ==================== Tab 3：按职位 ==================== -->
        <el-tab-pane label="按职位" name="position">
          <div class="toolbar">
            <el-select v-model="selectedPositionId" filterable placeholder="选择职位" class="user-select" @change="onPositionChange">
              <el-option v-for="p in positions" :key="p.id" :label="`${p.positionName}（${p.holderCount}人）`" :value="p.id" />
            </el-select>
            <el-button type="primary" plain @click="openPositionDialog(null)">新建职位</el-button>
            <el-button type="primary" plain :disabled="!selectedPositionId" @click="openPositionDialog(selectedPosition ?? null)">
              编辑
            </el-button>
            <el-button type="danger" plain :disabled="!selectedPositionId" @click="onDeletePosition">删除</el-button>
          </div>
          <perm-tree
            ref="posPermTreeRef"
            v-if="selectedPositionId"
            subject-type="POSITION"
            :subject-id="selectedPositionId"
            @changed="onPermChanged"
          />
          <el-empty v-else description="请先选择职位" />
        </el-tab-pane>

        <!-- ==================== Tab 4：变更日志 ==================== -->
        <el-tab-pane label="变更日志" name="log">
          <div class="toolbar">
            <el-select v-model="logQuery.subjectType" placeholder="主体类型" clearable class="log-select">
              <el-option label="用户" value="USER" />
              <el-option label="部门" value="DEPT" />
              <el-option label="职位" value="POSITION" />
            </el-select>
            <el-select v-model="logQuery.dimension" placeholder="维度" clearable class="log-select">
              <el-option label="部门维度" value="DEPT" />
              <el-option label="固定职位" value="FIXED" />
              <el-option label="临时职位" value="TEMP" />
              <el-option label="职位配置" value="POSITION" />
            </el-select>
            <el-input v-model="logQuery.operatorName" placeholder="操作人" clearable class="log-input" />
            <el-date-picker
              v-model="logTimeRange"
              type="datetimerange"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="log-date"
            />
            <el-button type="primary" @click="loadLogs">查询</el-button>
          </div>

          <el-table :data="logList" size="small" border>
            <el-table-column label="时间" prop="createTime" width="165" />
            <el-table-column label="操作人" prop="operatorName" width="100" />
            <el-table-column label="主体" width="150">
              <template #default="{ row }">{{ subjectLabel(row.subjectType) }}·{{ row.subjectName || row.subjectId }}</template>
            </el-table-column>
            <el-table-column label="维度" width="90">
              <template #default="{ row }">{{ dimensionLabel(row.dimension) }}</template>
            </el-table-column>
            <el-table-column label="操作" prop="operation" width="140" />
            <el-table-column label="涉及权限点" min-width="200">
              <template #default="{ row }">
                <span class="perm-codes">{{ briefPerms(row.permCodes) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变更内容" width="100">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showLogDetail(row as PermChangeLog)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="logQuery.pageNo"
            :page-size="logQuery.pageSize"
            :total="logTotal"
            layout="total, prev, pager, next"
            @current-change="loadLogs"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 权限预览抽屉 -->
    <el-drawer v-model="previewVisible" title="权限预览（模拟生效结果）" size="480px">
      <template v-if="previewData">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用户">{{ previewData.realName }}（{{ previewData.username }}）</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ previewData.deptName || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="精细控制状态">
            <el-tag :type="previewData.finePermEnabled ? 'warning' : 'success'" size="small">
              {{ previewData.finePermEnabled ? '已启用精细控制' : '未启用（未配置任何维度）' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="角色">{{ previewData.roles.join('、') || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">维度来源明细</div>
        <el-empty v-if="!previewData.dimensions.length" description="无任何维度配置" :image-size="60" />
        <div v-for="(d, i) in previewData.dimensions" :key="i" class="dim-block">
          <div class="dim-header">
            <el-tag :type="d.dimension === 'TEMP' ? 'warning' : d.dimension === 'FIXED' ? 'success' : 'primary'" size="small">
              {{ dimensionLabel(d.dimension) }}
            </el-tag>
            <span class="dim-source">{{ d.sourceName }}</span>
            <span class="dim-count">{{ d.perms.length }} 项</span>
          </div>
          <div class="perm-codes">{{ d.perms.join('、') }}</div>
        </div>

        <template v-if="previewData.conflicts.length">
          <div class="section-title">冲突裁决（临时 &gt; 固定 &gt; 部门）</div>
          <div v-for="(c, i) in previewData.conflicts" :key="i" class="conflict-item">
            <div class="conflict-code">{{ c.permCode }}</div>
            <div class="conflict-detail">{{ c.detail }}</div>
          </div>
        </template>

        <div class="section-title">最终生效权限（{{ previewData.grantedPerms.length }} 项）</div>
        <div class="perm-codes granted">{{ previewData.grantedPerms.join('、') || '无' }}</div>
      </template>
    </el-drawer>

    <!-- 分配职位对话框 -->
    <el-dialog v-model="assignPosVisible" title="分配职位" width="440px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="职位">
          <el-select v-model="assignPosForm.positionId" filterable placeholder="选择职位">
            <el-option v-for="p in positions" :key="p.id" :label="p.positionName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="性质">
          <el-radio-group v-model="assignPosForm.positionKind">
            <el-radio value="FIXED">固定职位（长期有效）</el-radio>
            <el-radio value="TEMP">临时职位（需有效期）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="assignPosForm.positionKind === 'TEMP'" label="有效期">
          <el-date-picker
            v-model="assignPosRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignPosVisible = false">取消</el-button>
        <el-button type="primary" @click="onAssignPosition">确定</el-button>
      </template>
    </el-dialog>

    <!-- 职位编辑对话框 -->
    <el-dialog v-model="posDialogVisible" :title="posForm.id ? '编辑职位' : '新建职位'" width="440px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="职位编码">
          <el-input v-model="posForm.positionCode" :disabled="!!posForm.id" placeholder="如 PRJ_SPECIALIST" />
        </el-form-item>
        <el-form-item label="职位名称">
          <el-input v-model="posForm.positionName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="posForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="posForm.id" label="状态">
          <el-switch v-model="posForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="posDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSavePosition">保存</el-button>
      </template>
    </el-dialog>

    <!-- 日志详情对话框 -->
    <el-dialog v-model="logDetailVisible" title="变更详情" width="640px" :close-on-click-modal="false">
      <template v-if="logDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="操作人">{{ logDetail.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ logDetail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="主体">{{ subjectLabel(logDetail.subjectType) }}·{{ logDetail.subjectName || logDetail.subjectId }}</el-descriptions-item>
          <el-descriptions-item label="操作/维度">{{ logDetail.operation }} / {{ dimensionLabel(logDetail.dimension) }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-title">变更前</div>
        <pre class="json-block">{{ pretty(logDetail.beforeJson) }}</pre>
        <div class="section-title">变更后</div>
        <pre class="json-block">{{ pretty(logDetail.afterJson) }}</pre>
        <div v-if="logDetail.remark" class="dim-text">备注：{{ logDetail.remark }}</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  previewUserPermsApi, getPositionsApi, createPositionApi,
  updatePositionApi, deletePositionApi, getUserPositionsApi, assignUserPositionApi,
  removeUserPositionApi, getChangeLogsApi, type FinePermPreview, type Position,
  type UserPosition, type PermChangeLog
} from '@/api/finePerm'
import { getUserOptionsApi, type SysUser } from '@/api/user'
import { listDeptsApi, type SysDept } from '@/api/dept'
import PermTree from './components/PermTree.vue'

// ---------- Tab 与选中主体 ----------
const activeTab = ref('user')
const userOptions = ref<SysUser[]>([])
const selectedUserId = ref<number>()
const selectedUserDept = ref<{ id: number; deptName: string } | null>(null)
const userPositions = ref<UserPosition[]>([])

const deptTreeData = ref<any[]>([])
const selectedDeptId = ref<number>()

const positions = ref<Position[]>([])
const selectedPositionId = ref<number>()
const selectedPosition = computed(() => positions.value.find(p => p.id === selectedPositionId.value))

// ---------- 按用户 ----------
async function loadUserOptions() {
  const res = await getUserOptionsApi()
  userOptions.value = res.data || []
}

async function onUserChange() {
  if (!selectedUserId.value) return
  const res = await getUserPositionsApi(selectedUserId.value)
  userPositions.value = res.data || []
  // 部门维度快捷配置入口：取用户 deptId，部门名从组织架构树查找
  const user = userOptions.value.find(u => u.id === selectedUserId.value)
  if (user?.deptId) {
    selectedUserDept.value = {
      id: user.deptId,
      deptName: findDeptName(deptTreeData.value, user.deptId) || `部门#${user.deptId}`
    }
  } else {
    selectedUserDept.value = null
  }
}

function findDeptName(nodes: any[], id: number): string | null {
  for (const n of nodes) {
    if (n.id === id) return n.deptName
    if (n.children?.length) {
      const found = findDeptName(n.children, id)
      if (found) return found
    }
  }
  return null
}

function openAssignPosition() {
  assignPosForm.positionId = undefined
  assignPosForm.positionKind = 'FIXED'
  assignPosRange.value = []
  assignPosVisible.value = true
}

async function onAssignPosition() {
  if (!selectedUserId.value || !assignPosForm.positionId) {
    ElMessage.warning('请选择职位')
    return
  }
  if (assignPosForm.positionKind === 'TEMP' && (!assignPosRange.value || assignPosRange.value.length !== 2)) {
    ElMessage.warning('临时职位必须配置有效期')
    return
  }
  try {
    await assignUserPositionApi(selectedUserId.value, {
      positionId: assignPosForm.positionId,
      positionKind: assignPosForm.positionKind,
      startTime: assignPosForm.positionKind === 'TEMP' ? assignPosRange.value[0] : undefined,
      endTime: assignPosForm.positionKind === 'TEMP' ? assignPosRange.value[1] : undefined
    })
    ElMessage.success('职位分配成功，权限已实时生效')
    assignPosVisible.value = false
    await onUserChange()
    await loadPositions()
  } catch (e: any) {
    ElMessage.error(e?.message || '分配失败')
  }
}

async function onRemoveUserPosition(row: UserPosition) {
  await ElMessageBox.confirm(`确认移除职位「${row.positionName}」？移除后相关权限立即回收`, '确认', { type: 'warning' })
  await removeUserPositionApi(row.id, '界面移除')
  ElMessage.success('已移除，权限实时回收')
  await onUserChange()
  await loadPositions()
}

// ---------- 按部门 ----------
/** 扁平部门列表转树（parentId 归组） */
function buildDeptTree(list: SysDept[], parentId = 0): any[] {
  return list
    .filter(d => (d.parentId ?? 0) === parentId)
    .map(d => ({ ...d, children: buildDeptTree(list, d.id) }))
    .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
}

async function loadDeptTree() {
  const res = await listDeptsApi()
  deptTreeData.value = buildDeptTree(res.data || [])
}

function onDeptChange() {
  // PermTree 响应 subjectId 变化
}

function openPreviewOfDeptUser() {
  // 部门维度预览：提示使用按用户 Tab 预览（部门是维度，预览以用户为主体）
  ElMessage.info('权限预览以用户为主体：请切换到「按用户」选择成员进行预览')
}

// ---------- 按职位 ----------
async function loadPositions() {
  const res = await getPositionsApi()
  positions.value = res.data || []
}

function onPositionChange() {
  // PermTree 响应 subjectId 变化
}

function openPositionDialog(pos: Position | null) {
  posForm.id = pos?.id
  posForm.positionCode = pos?.positionCode || ''
  posForm.positionName = pos?.positionName || ''
  posForm.description = pos?.description || ''
  posForm.status = pos?.status ?? 1
  posDialogVisible.value = true
}

async function onSavePosition() {
  if (!posForm.positionCode || !posForm.positionName) {
    ElMessage.warning('职位编码与名称必填')
    return
  }
  try {
    if (posForm.id) {
      await updatePositionApi(posForm.id, {
        positionName: posForm.positionName,
        description: posForm.description,
        status: posForm.status
      })
      ElMessage.success('职位已更新')
    } else {
      await createPositionApi({
        positionCode: posForm.positionCode,
        positionName: posForm.positionName,
        description: posForm.description
      })
      ElMessage.success('职位已创建')
    }
    posDialogVisible.value = false
    await loadPositions()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function onDeletePosition() {
  if (!selectedPosition.value) return
  await ElMessageBox.confirm(`确认删除职位「${selectedPosition.value.positionName}」？`, '确认', { type: 'warning' })
  try {
    await deletePositionApi(selectedPosition.value.id)
    ElMessage.success('已删除')
    selectedPositionId.value = undefined
    await loadPositions()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败（职位被持有时不可删除）')
  }
}

// ---------- 权限变更联动 ----------
function onPermChanged() {
  // 权限变更后版本号变化，前端权限将在下次路由切换时自动刷新（30s 节流）
  ElMessage.success('权限配置已保存并实时生效')
}

// ---------- 预览 ----------
const previewVisible = ref(false)
const previewData = ref<FinePermPreview | null>(null)

async function openPreview(userId?: number) {
  if (!userId) return
  const res = await previewUserPermsApi(userId)
  previewData.value = res.data
  previewVisible.value = true
}

// ---------- 变更日志 ----------
const logQuery = reactive({
  subjectType: undefined as 'USER' | 'DEPT' | 'POSITION' | undefined,
  dimension: undefined as string | undefined,
  operatorName: '',
  pageNo: 1,
  pageSize: 20
})
const logTimeRange = ref<string[]>([])
const logList = ref<PermChangeLog[]>([])
const logTotal = ref(0)
const logDetailVisible = ref(false)
const logDetail = ref<PermChangeLog | null>(null)

async function loadLogs() {
  const res = await getChangeLogsApi({
    ...logQuery,
    operatorName: logQuery.operatorName || undefined,
    startTime: logTimeRange.value?.[0],
    endTime: logTimeRange.value?.[1]
  })
  logList.value = res.data?.records || []
  logTotal.value = Number(res.data?.total || 0)
}

function showLogDetail(row: PermChangeLog) {
  logDetail.value = row
  logDetailVisible.value = true
}

// ---------- 工具 ----------
function subjectLabel(t?: string) {
  return t === 'USER' ? '用户' : t === 'DEPT' ? '部门' : '职位'
}

function dimensionLabel(d?: string) {
  return d === 'DEPT' ? '部门维度' : d === 'FIXED' ? '固定职位' : d === 'TEMP' ? '临时职位' : d === 'POSITION' ? '职位配置' : d || '-'
}

function briefPerms(json?: string) {
  if (!json) return '-'
  try {
    const arr = JSON.parse(json) as string[]
    return arr.length > 5 ? arr.slice(0, 5).join('、') + ` 等${arr.length}项` : arr.join('、')
  } catch {
    return json
  }
}

function pretty(json?: string) {
  if (!json) return '（无）'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function formatDate(d?: string) {
  return d ? String(d).replace('T', ' ').slice(0, 16) : '-'
}

// ---------- 表单状态 ----------
const assignPosVisible = ref(false)
const assignPosForm = reactive<{ positionId?: number; positionKind: 'FIXED' | 'TEMP' }>({ positionKind: 'FIXED' })
const assignPosRange = ref<string[]>([])
const posDialogVisible = ref(false)
const posForm = reactive<{ id?: number; positionCode: string; positionName: string; description?: string; status: number }>({
  positionCode: '',
  positionName: '',
  description: '',
  status: 1
})

onMounted(async () => {
  await Promise.all([loadUserOptions(), loadDeptTree(), loadPositions(), loadLogs()])
})
</script>

<style scoped lang="scss">
.intro-alert {
  margin-bottom: 16px;
}

.user-select {
  width: 260px;
}

.log-select {
  width: 130px;
}

.log-input {
  width: 140px;
}

.log-date {
  width: 340px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0;
}

.section-title {
  font-weight: 600;
  font-size: 14px;
  margin: 16px 0 8px;
}

.dim-text {
  color: var(--dm-muted, #767676);
  font-size: 13px;
}

.dim-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
}

.dim-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dim-source {
  font-weight: 600;
}

.dim-count {
  margin-left: auto;
  color: var(--dm-muted, #767676);
  font-size: 12px;
}

.perm-codes {
  font-size: 12px;
  line-height: 1.8;
  word-break: break-all;

  &.granted {
    color: var(--el-color-success);
  }
}

.conflict-item {
  border-left: 3px solid var(--el-color-warning);
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 8px 10px;
  margin-bottom: 8px;
}

.conflict-code {
  font-family: monospace;
  font-size: 12px;
  font-weight: 600;
}

.conflict-detail {
  font-size: 12px;
  color: var(--dm-muted, #767676);
  margin-top: 4px;
}

.json-block {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
