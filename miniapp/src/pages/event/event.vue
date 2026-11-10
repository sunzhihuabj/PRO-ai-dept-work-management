<template>
  <view class="page">
    <view class="card">
      <text class="title">新建事件</text>
      <view class="field">
        <text class="label">标题</text>
        <input v-model="form.eventTitle" class="ipt" maxlength="100" />
      </view>
      <view class="field">
        <text class="label">所属项目</text>
        <picker :range="projectNames" @change="onProjectPick">
          <view class="ipt picker">{{ projectNames[form.projectIndex] || '请选择项目' }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">类型</text>
        <picker :range="typeNames" @change="onTypePick">
          <view class="ipt picker">{{ typeNames[form.typeIndex] }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">紧急程度</text>
        <picker :range="urgencyNames" @change="onUrgencyPick">
          <view class="ipt picker">{{ urgencyNames[form.urgencyIndex] }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">描述</text>
        <textarea v-model="form.description" class="ipt ta" maxlength="1000" />
      </view>
      <button class="btn-primary btn-block" :loading="submitting" @click="submit">提交事件</button>
    </view>

    <view class="card">
      <text class="title">我创建的事件</text>
      <view v-for="e in list" :key="e.id" class="evt-item">
        <view class="row-top">
          <text class="t-title">{{ e.eventTitle }}</text>
          <text class="tag" :class="statusTag(e.status)">{{ statusText(e.status) }}</text>
        </view>
        <text class="sub">类型：{{ typeText(e.eventType) }} · 紧急度：{{ urgencyText(e.urgency) }} · {{ e.createTime || '' }}</text>
      </view>
      <view v-if="!list.length" class="empty">暂无事件</view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getEventListApi, createEventApi, getMyCreatedEventsApi,
  getProjectListApi, getToken, getUserInfo
} from '@/api'

const form = reactive({ eventTitle: '', projectIndex: 0, typeIndex: 0, urgencyIndex: 1, description: '' })
const projects = ref([])
const list = ref([])
const submitting = ref(false)

const typeNames = ['售后', '变更', '异常', '其他']
const typeValues = [1, 2, 3, 99]
const urgencyNames = ['紧急', '一般', '低']
const urgencyValues = [1, 2, 3]
const projectNames = computed(() => projects.value.map(p => p.projectName))

const typeText = (t) => ({ 1: '售后', 2: '变更', 3: '异常', 99: '其他' }[t] || '其他')
const urgencyText = (u) => ({ 1: '紧急', 2: '一般', 3: '低' }[u] || '一般')
const statusText = (s) => ['待审批', '处理中', '已完成', '已关闭', '已驳回'][s] || '未知'
const statusTag = (s) => ['tag-warning', 'tag-primary', 'tag-success', 'tag-info', 'tag-danger'][s] || 'tag-info'

function onProjectPick(e) { form.projectIndex = Number(e.detail.value) }
function onTypePick(e) { form.typeIndex = Number(e.detail.value) }
function onUrgencyPick(e) { form.urgencyIndex = Number(e.detail.value) }

onShow(async () => {
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  try {
    const [pRes, evRes] = await Promise.all([
      getProjectListApi({ pageNum: 1, pageSize: 100 }),
      getMyCreatedEventsApi()
    ])
    projects.value = (pRes && pRes.records) || []
    list.value = evRes || []
  } catch (e) {}
})

async function submit() {
  const u = getUserInfo()
  if (!form.eventTitle) {
    uni.showToast({ title: '请输入标题', icon: 'none' })
    return
  }
  const proj = projects.value[form.projectIndex]
  if (!proj) {
    uni.showToast({ title: '请选择项目', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createEventApi({
      projectId: proj.id,
      eventType: typeValues[form.typeIndex],
      eventTitle: form.eventTitle,
      urgency: urgencyValues[form.urgencyIndex],
      description: form.description,
      handlerId: u ? u.id : undefined
    })
    uni.showToast({ title: '提交成功', icon: 'success' })
    form.eventTitle = ''
    form.description = ''
    list.value = await getMyCreatedEventsApi()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
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
.ta { height: 160rpx; }
.picker { color: #303133; }
.row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-title { font-size: 28rpx; font-weight: 500; flex: 1; }
.evt-item {
  border-bottom: 1rpx solid #f0f0f0;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.evt-item:last-child { border-bottom: none; }
</style>
