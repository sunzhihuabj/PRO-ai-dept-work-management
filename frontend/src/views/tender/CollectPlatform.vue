<template>
  <div class="page-container">
    <el-page-header style="margin-bottom: 16px;" @back="$router.back()">
      <template #content>
        <span class="page-header-title">标讯采集平台管理</span>
        <span class="page-header-sub">AGT-27 联网采集：平台配置 → 自动登录 → 采集提取 → 自动登记</span>
      </template>
    </el-page-header>

    <el-card shadow="hover" class="mb-16">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="平台名称搜索" clearable style="width: 220px" @keyup.enter="loadPlatforms" />
        <el-button type="primary" :icon="Search" @click="loadPlatforms">搜索</el-button>
        <div class="spacer" />
        <el-button v-if="userStore.isAdmin" type="success" :icon="Plus" @click="openCreate">新增平台</el-button>
      </div>

      <el-table :data="platforms" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="platformName" label="平台名称" min-width="160" />
        <el-table-column prop="authType" label="认证方式" width="90">
          <template #default="{ row }">
            <el-tag :type="row.authType === 'NONE' ? 'info' : row.authType === 'FORM' ? 'warning' : 'success'" size="small">
              {{ row.authType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭据" width="80">
          <template #default="{ row }">
            <el-tag :type="row.ext?.authConfigured ? 'success' : 'danger'" size="small">
              {{ row.ext?.authConfigured ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="listUrl" label="列表页 URL" min-width="220" show-overflow-tooltip />
        <el-table-column label="限频" width="110">
          <template #default="{ row }">{{ row.requestIntervalMs }}+{{ row.jitterMs }}ms</template>
        </el-table-column>
        <el-table-column prop="maxPages" label="最大页数" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="row._running" @click="trigger(row)">采集</el-button>
            <el-button size="small" :loading="row._testing" @click="testLogin(row)">测登录</el-button>
            <el-button size="small" :icon="Edit" circle @click="openEdit(row)" />
            <el-button size="small" :icon="SwitchButton" circle @click="toggle(row)" />
            <el-popconfirm title="确认删除该平台配置？" @confirm="remove(row)">
              <template #reference>
                <el-button size="small" type="danger" :icon="Delete" circle />
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        class="mt-8"
        @current-change="loadPlatforms"
      />
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div class="toolbar">
          <span>采集运行监控</span>
          <el-select v-model="runStatus" placeholder="全部状态" clearable style="width: 140px" @change="loadRuns">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="部分成功" value="PARTIAL" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
          <el-button :icon="Refresh" circle @click="loadRuns" />
        </div>
      </template>
      <el-table :data="runs" v-loading="runsLoading" border size="small">
        <el-table-column prop="id" label="运行#" width="70" />
        <el-table-column prop="platformName" label="平台" min-width="140" />
        <el-table-column prop="triggerType" label="触发" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="runTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录" width="100">
          <template #default="{ row }">
            <el-tag :type="row.loginStatus === 'FAILED' ? 'danger' : 'info'" size="small">{{ row.loginStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pagesFetched" label="页数" width="60" />
        <el-table-column prop="itemsFound" label="发现" width="60" />
        <el-table-column prop="registerOk" label="登记" width="60" />
        <el-table-column prop="registerDup" label="去重" width="60" />
        <el-table-column prop="extractFailed" label="待核验" width="70" />
        <el-table-column prop="startedAt" label="开始时间" width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="showRun(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="runPageNum"
        :page-size="runPageSize"
        :total="runTotal"
        layout="prev, pager, next, total"
        class="mt-8"
        @current-change="loadRuns"
      />
    </el-card>

    <!-- 平台编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑采集平台' : '新增采集平台'" width="720px" :close-on-click-modal="false">
      <el-form :model="form" label-width="130px">
        <el-form-item label="平台名称" required>
          <el-input v-model="form.platformName" placeholder="如：国家电网电子商务平台" />
        </el-form-item>
        <el-form-item label="认证方式" required>
          <el-radio-group v-model="form.authType">
            <el-radio value="NONE">免登录</el-radio>
            <el-radio value="FORM">账号密码</el-radio>
            <el-radio value="COOKIE">Cookie 复用</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.authType === 'FORM'">
          <el-form-item label="登录入口 URL" required>
            <el-input v-model="form.loginUrl" placeholder="https://platform.example.com/login" />
          </el-form-item>
        </template>
        <el-form-item v-if="form.authType !== 'NONE'" label="登录凭据">
          <template v-if="form.authType === 'FORM'">
            <el-input v-model="form.username" :placeholder="editing && editing.ext?.authConfigured ? '留空=不修改账号' : '登录账号'" style="margin-bottom: 8px" />
            <el-input v-model="form.password" type="password" show-password
                      :placeholder="editing && editing.ext?.authConfigured ? '留空=不修改密码' : '登录密码'" />
            <div class="form-tip">凭据经 AES-256-GCM 加密落库，任何接口不回显明文</div>
          </template>
          <template v-else>
            <el-input v-model="form.cookie" type="textarea" :rows="3"
                      :placeholder="editing && editing.ext?.authConfigured ? '留空=不修改 Cookie；粘贴浏览器登录后的 Cookie 串' : '粘贴浏览器登录后的 Cookie 串（k=v; k2=v2）'" />
          </template>
        </el-form-item>
        <el-form-item label="列表页 URL" required>
          <el-input v-model="form.listUrl" placeholder="支持 {page} 占位符，如 https://x.com/list/{page}；或不带占位符配合 pageParam" />
        </el-form-item>
        <el-form-item label="详情页 URL 前缀">
          <el-input v-model="form.detailUrlPrefix" placeholder="列表项为相对链接时必填，如 https://x.com/notice/" />
        </el-form-item>
        <el-form-item label="采集规则" required>
          <el-input v-model="form.collectRules" type="textarea" :rows="10" spellcheck="false" />
          <div class="form-tip">
            mode=HTML（itemSelector/titleSelector/linkSelector/dateSelector）或 JSON_API（itemsPath/titleField/linkField）；
            分页 pageParam / {page}；详情 detail.contentSelector；登录 login.usernameField/passwordField/successKeyword
          </div>
        </el-form-item>
        <el-form-item label="请求间隔(ms)">
          <el-input-number v-model="form.requestIntervalMs" :min="500" :step="100" />
          <span class="form-tip" style="margin-left: 12px">实际间隔 = 间隔 + 0~{{ form.jitterMs }}ms 随机抖动（拟人反爬）</span>
        </el-form-item>
        <el-form-item label="抖动上限(ms)">
          <el-input-number v-model="form.jitterMs" :min="0" :step="100" />
        </el-form-item>
        <el-form-item label="单次最大页数">
          <el-input-number v-model="form.maxPages" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行详情抽屉 -->
    <el-drawer v-model="runDrawer" title="采集运行详情" size="45%">
      <template v-if="currentRun">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行#">{{ currentRun.id }}</el-descriptions-item>
          <el-descriptions-item label="平台">{{ currentRun.platformName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentRun.status }}</el-descriptions-item>
          <el-descriptions-item label="登录">{{ currentRun.loginStatus }}</el-descriptions-item>
          <el-descriptions-item label="页数">{{ currentRun.pagesFetched }}</el-descriptions-item>
          <el-descriptions-item label="发现/拉取">{{ currentRun.itemsFound }} / {{ currentRun.itemsCollected }}</el-descriptions-item>
          <el-descriptions-item label="提取全过">{{ currentRun.extractOk }}</el-descriptions-item>
          <el-descriptions-item label="二次核验补全">{{ currentRun.extractRepaired }}</el-descriptions-item>
          <el-descriptions-item label="登记/去重">{{ currentRun.registerOk }} / {{ currentRun.registerDup }}</el-descriptions-item>
          <el-descriptions-item label="待人工核验">{{ currentRun.extractFailed }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ currentRun.startedAt }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ currentRun.finishedAt }}</el-descriptions-item>
        </el-descriptions>
        <h4>断点检查点</h4>
        <el-input :model-value="pretty(currentRun.checkpoint)" type="textarea" :rows="4" readonly />
        <h4>异常现场快照</h4>
        <el-input :model-value="currentRun.errorSnapshot || '（无异常）'" type="textarea" :rows="8" readonly />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Edit, Delete, SwitchButton, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  pagePlatformsApi, createPlatformApi, updatePlatformApi, deletePlatformApi,
  togglePlatformApi, testLoginApi, triggerCollectApi, pageRunsApi, runDetailApi,
  type TenderPlatform, type TenderCollectRun
} from '@/api/tenderCollect'

const userStore = useUserStore()
const platforms = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

const runs = ref<TenderCollectRun[]>([])
const runsLoading = ref(false)
const runPageNum = ref(1)
const runPageSize = 10
const runTotal = ref(0)
const runStatus = ref('')
const runDrawer = ref(false)
const currentRun = ref<TenderCollectRun | null>(null)

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<TenderPlatform | null>(null)
const form = reactive<any>(defaultForm())

function defaultForm() {
  return {
    platformName: '', loginUrl: '', listUrl: '', detailUrlPrefix: '',
    authType: 'NONE', username: '', password: '', cookie: '',
    collectRules: JSON.stringify({
      mode: 'HTML',
      list: { itemSelector: '.news-list li', titleSelector: 'a.title', linkSelector: 'a.title', dateSelector: '.date', pageParam: 'page' },
      detail: { contentSelector: '.content' },
      login: { usernameField: 'username', passwordField: 'password', successKeyword: '退出' },
      userAgent: ''
    }, null, 2),
    requestIntervalMs: 1500, jitterMs: 800, maxPages: 3, remark: ''
  }
}

async function loadPlatforms() {
  loading.value = true
  try {
    const res = await pagePlatformsApi({ pageNum: pageNum.value, pageSize, keyword: keyword.value })
    platforms.value = (res.data?.records ?? []).map((r: any) => ({ ...r, _running: false, _testing: false }))
    total.value = Number(res.data?.total ?? 0)
  } catch (e) {
    ElMessage.error('加载平台列表失败')
  } finally {
    loading.value = false
  }
}

async function loadRuns() {
  runsLoading.value = true
  try {
    const res = await pageRunsApi({ pageNum: runPageNum.value, runPageSize, status: runStatus.value || undefined, pageSize: runPageSize } as any)
    runs.value = res.data?.records ?? []
    runTotal.value = Number(res.data?.total ?? 0)
  } catch (e) {
    ElMessage.error('加载运行记录失败')
  } finally {
    runsLoading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: any) {
  editing.value = row
  Object.assign(form, defaultForm(), {
    platformName: row.platformName,
    loginUrl: row.loginUrl,
    listUrl: row.listUrl,
    detailUrlPrefix: row.detailUrlPrefix,
    authType: row.authType,
    collectRules: row.collectRules,
    requestIntervalMs: row.requestIntervalMs,
    jitterMs: row.jitterMs,
    maxPages: row.maxPages,
    remark: row.remark,
    username: '', password: '', cookie: ''
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.platformName || !form.listUrl) {
    ElMessage.error('平台名称与列表页 URL 必填')
    return
  }
  // 组装 authParams（仅实际填写了才提交；编辑留空=保留原凭据）
  let authParams: Record<string, string> | undefined = undefined
  if (form.authType === 'FORM' && (form.username || form.password)) {
    authParams = {}
    if (form.username) authParams.username = form.username
    if (form.password) authParams.password = form.password
  } else if (form.authType === 'COOKIE' && form.cookie) {
    authParams = { cookie: form.cookie }
  }
  saving.value = true
  try {
    const payload: any = {
      platformName: form.platformName, loginUrl: form.loginUrl, listUrl: form.listUrl,
      detailUrlPrefix: form.detailUrlPrefix, authType: form.authType, authParams,
      collectRules: form.collectRules, requestIntervalMs: form.requestIntervalMs,
      jitterMs: form.jitterMs, maxPages: form.maxPages, remark: form.remark
    }
    if (editing.value) {
      await updatePlatformApi(editing.value.id!, payload)
    } else {
      await createPlatformApi(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadPlatforms()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  try {
    await deletePlatformApi(row.id)
    ElMessage.success('已删除')
    loadPlatforms()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function toggle(row: any) {
  try {
    await togglePlatformApi(row.id, row.enabled === 1 ? 0 : 1)
    ElMessage.success('已更新')
    loadPlatforms()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function testLogin(row: any) {
  row._testing = true
  try {
    const res: any = await testLoginApi(row.id)
    if (res.data?.success) {
      ElMessage.success('登录/会话校验通过')
    } else {
      ElMessage.error('登录/会话校验未通过，请检查凭据或平台可达性')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  } finally {
    row._testing = false
  }
}

async function trigger(row: any) {
  row._running = true
  try {
    const res: any = await triggerCollectApi(row.id)
    const run = res.data
    ElMessage.success(`采集完成（${run.status}）：发现 ${run.itemsFound} 条，登记 ${run.registerOk} 条，去重 ${run.registerDup} 条，待核验 ${run.extractFailed} 条`)
    loadRuns()
  } catch (e: any) {
    ElMessage.error(e?.message || '采集失败（详情见运行监控）')
    loadRuns()
  } finally {
    row._running = false
  }
}

async function showRun(row: any) {
  try {
    const res: any = await runDetailApi(row.id)
    currentRun.value = res.data
    runDrawer.value = true
  } catch (e) {
    ElMessage.error('加载运行详情失败')
  }
}

function pretty(json?: string) {
  try {
    return json ? JSON.stringify(JSON.parse(json), null, 2) : '（无检查点）'
  } catch (e) {
    return json || ''
  }
}

function runTag(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

onMounted(() => {
  loadPlatforms()
  loadRuns()
})
</script>

<style scoped lang="scss">
.page-header-title { font-size: 16px; font-weight: bold; }
.page-header-sub { font-size: 12px; color: var(--dm-muted); margin-left: 12px; }
.mb-16 { margin-bottom: 16px; }
.mt-8 { margin-top: 8px; }
.toolbar { display: flex; align-items: center; gap: 8px; .spacer { flex: 1; } }
.form-tip { font-size: 12px; color: var(--dm-muted); line-height: 1.6; }
</style>
