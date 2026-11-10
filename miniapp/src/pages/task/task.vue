<template>
  <view class="page">
    <view class="card">
      <input v-model="keyword" class="ipt" placeholder="搜索任务名称" @confirm="loadData" />
    </view>

    <view v-for="t in list" :key="t.id" class="card" @click="goDetail(t.id)">
      <view class="row-top">
        <text class="t-title">{{ t.taskName }}</text>
        <text class="tag" :class="statusTag(t.status)">{{ statusText(t.status) }}</text>
      </view>
      <view class="row-bottom">
        <text class="sub">优先级：{{ pText(t.priority) }}</text>
        <text class="sub">进度 {{ t.progress }}%</text>
        <text class="sub">截止 {{ t.deadline || '-' }}</text>
      </view>
    </view>
    <view v-if="!list.length" class="empty">暂无任务</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTaskListApi, getToken } from '@/api'

const keyword = ref('')
const list = ref([])

const pText = (p) => p === 1 ? '高' : p === 3 ? '低' : '中'
const statusText = (s) => ['待办', '进行中', '待审核', '已完成', '已关闭'][s] || '未知'
const statusTag = (s) => ['tag-info', 'tag-primary', 'tag-warning', 'tag-success', 'tag-danger'][s] || 'tag-info'
const goDetail = (id) => uni.navigateTo({ url: '/pages/task/detail?id=' + id })

onShow(async () => {
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  loadData()
})

async function loadData() {
  try {
    const data = await getTaskListApi({ pageNum: 1, pageSize: 20, keyword: keyword.value })
    list.value = (data && data.records) || []
  } catch (e) {}
}
</script>

<style scoped>
.ipt {
  border: 2rpx solid #dcdfe6;
  border-radius: 12rpx;
  padding: 16rpx 24rpx;
  font-size: 26rpx;
}
.row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-title { font-size: 30rpx; font-weight: 600; flex: 1; }
.row-bottom {
  display: flex;
  gap: 20rpx;
  margin-top: 12rpx;
}
</style>
