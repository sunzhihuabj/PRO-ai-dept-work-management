<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">操作日志</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div class="search-bar">
        <el-input v-model="query.title" placeholder="操作模块" clearable style="width: 160px" @keyup.enter="loadData" />
        <el-input v-model="query.operName" placeholder="操作人" clearable style="width: 140px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="结果" clearable style="width: 110px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="模块" width="100" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }: any">{{ bizText(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="requestMethod" label="方式" width="70" align="center" />
        <el-table-column prop="operUrl" label="URL" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operName" label="操作人" width="100" />
        <el-table-column prop="operIp" label="IP" width="120" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column label="详情" width="70" align="center">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end; display: flex;"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="模块">{{ current?.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ bizText(current?.businessType) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ current?.operName }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ current?.operIp }}</el-descriptions-item>
        <el-descriptions-item label="URL" :span="2">{{ current?.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">{{ current?.method }}</el-descriptions-item>
        <el-descriptions-item label="耗时(ms)">{{ current?.costTime ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ current?.operTime }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">请求参数</el-divider>
      <pre class="json-box">{{ pretty(current?.operParam) }}</pre>
      <el-divider content-position="left">返回结果</el-divider>
      <pre class="json-box">{{ pretty(current?.jsonResult) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { listOperLogsApi, type SysOperLog } from '@/api/operlog'

const loading = ref(false)
const logs = ref<SysOperLog[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1, pageSize: 10,
  title: '', operName: '', status: undefined as number | undefined
})
const detailVisible = ref(false)
const current = ref<SysOperLog | null>(null)

const bizText = (t?: number) => t === 1 ? '新增' : t === 2 ? '修改' : t === 3 ? '删除' : '其它'
const pretty = (json?: string) => {
  if (!json) return '-'
  try { return JSON.stringify(JSON.parse(json), null, 2) } catch (e) { return json }
}

async function loadData() {
  loading.value = true
  try {
    const res = await listOperLogsApi(query)
    logs.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.title = ''
  query.operName = ''
  query.status = undefined
  query.pageNum = 1
  loadData()
}

function showDetail(row: SysOperLog) {
  current.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.search-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.json-box {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 220px;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  margin: 0;
}
</style>
