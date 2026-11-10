<template>
  <view class="page">
    <view class="card" v-if="task">
      <view class="row-top">
        <text class="t-title">{{ task.taskName }}</text>
        <text class="tag" :class="statusTag(task.status)">{{ statusText(task.status) }}</text>
      </view>
      <view class="desc">{{ task.description || '（无描述）' }}</view>
      <view class="grid">
        <text class="sub">进度：{{ task.progress }}%</text>
        <text class="sub">计划工时：{{ task.planHours || 0 }}h</text>
        <text class="sub">实际工时：{{ task.actualHours || 0 }}h</text>
        <text class="sub">截止：{{ task.deadline || '-' }}</text>
      </view>
    </view>

    <view class="card" v-if="canOperate">
      <text class="title">状态操作</text>
      <view class="btn-row">
        <button v-if="task && task.status === 0" class="btn-primary op-btn" @click="changeStatus(1)">开始执行</button>
        <button v-if="task && task.status === 1" class="btn-primary op-btn" @click="changeStatus(2)">提交审核</button>
        <button v-if="task && task.status === 2" class="btn-primary op-btn" @click="changeStatus(3)">审核通过</button>
        <button v-if="task && task.status === 1" class="op-btn btn-plain" @click="openHours">录入工时</button>
      </view>
    </view>

    <view class="card">
      <text class="title">工时记录</text>
      <view v-for="h in hours" :key="h.id" class="hour-item">
        <text class="hour-num">{{ h.hours }}h</text>
        <view class="hour-body">
          <text class="sub">{{ h.workDate }}</text>
          <text class="sub">{{ h.description || '' }}</text>
        </view>
      </view>
      <view v-if="!hours.length" class="empty">暂无工时记录</view>
    </view>

    <view v-if="showHours" class="mask" @click="showHours = false">
      <view class="dialog" @click.stop>
        <text class="title">录入工时</text>
        <view class="field">
          <text class="label">工时(h)</text>
          <input v-model="hourForm.hours" class="ipt" type="digit" />
        </view>
        <view class="field">
          <text class="label">日期</text>
          <input v-model="hourForm.workDate" class="ipt" placeholder="YYYY-MM-DD" />
        </view>
        <view class="field">
          <text class="label">工作内容</text>
          <input v-model="hourForm.description" class="ipt" />
        </view>
        <button class="btn-primary btn-block" @click="submitHours">提交</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getTaskDetailApi, changeTaskStatusApi, addWorkHourApi, getMyTodoApi, getUserInfo } from '@/api'
import http from '@/utils/request'

const task = ref(null)
const hours = ref([])
const showHours = ref(false)
const hourForm = reactive({ hours: '', workDate: '', description: '' })
let taskId = null

const statusText = (s) => ['待办', '进行中', '待审核', '已完成', '已关闭'][s] || '未知'
const statusTag = (s) => ['tag-info', 'tag-primary', 'tag-warning', 'tag-success', 'tag-danger'][s] || 'tag-info'

// 当前用户是否任务负责人（简化权限：负责人可见操作）
const canOperate = computed(() => {
  const u = getUserInfo()
  return task.value && u && task.value.assigneeId === u.id && [0, 1, 2].includes(task.value.status)
})

onLoad(async (opt) => {
  taskId = opt.id
  await loadData()
})

async function loadData() {
  try {
    task.value = await getTaskDetailApi(taskId)
    hours.value = await http.get(`/v1/tasks/${taskId}/hours`) || []
  } catch (e) {}
}

async function changeStatus(target) {
  try {
    await changeTaskStatusApi(taskId, target, '移动端操作')
    uni.showToast({ title: '操作成功', icon: 'success' })
    loadData()
  } catch (e) {}
}

function openHours() {
  const d = new Date()
  hourForm.workDate = d.toISOString().slice(0, 10)
  hourForm.hours = '8'
  hourForm.description = ''
  showHours.value = true
}

async function submitHours() {
  if (!hourForm.hours) {
    uni.showToast({ title: '请输入工时', icon: 'none' })
    return
  }
  try {
    await addWorkHourApi(taskId, {
      hours: Number(hourForm.hours),
      workDate: hourForm.workDate,
      description: hourForm.description
    })
    uni.showToast({ title: '录入成功', icon: 'success' })
    showHours.value = false
    loadData()
  } catch (e) {}
}
</script>

<style scoped>
.row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-title { font-size: 32rpx; font-weight: 600; flex: 1; }
.desc {
  margin-top: 16rpx;
  color: #606266;
  font-size: 26rpx;
  line-height: 1.6;
}
.grid {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.btn-row {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}
.op-btn {
  font-size: 26rpx;
  padding: 0 28rpx;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0;
}
.btn-plain {
  background: #ffffff;
  color: #409eff;
  border: 2rpx solid #409eff;
}
.hour-item {
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #f0f0f0;
  padding: 16rpx 0;
}
.hour-num { color: #409eff; font-weight: bold; width: 100rpx; }
.hour-body { flex: 1; display: flex; flex-direction: column; }
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99;
}
.dialog {
  background: #ffffff;
  border-radius: 16rpx;
  width: 80%;
  padding: 32rpx;
}
.field { margin-bottom: 24rpx; }
.label {
  display: block;
  font-size: 24rpx;
  color: #606266;
  margin-bottom: 8rpx;
}
.ipt {
  border: 2rpx solid #dcdfe6;
  border-radius: 10rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
}
</style>
