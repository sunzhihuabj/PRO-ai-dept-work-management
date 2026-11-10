<template>
  <view class="page">
    <view class="card">
      <view class="head-row">
        <text class="title">消息中心</text>
        <text class="link" @click="readAll">全部已读</text>
      </view>
      <view v-for="m in messages" :key="m.id" class="msg-item" :class="{ unread: m.isRead === 0 }" @click="readOne(m)">
        <view class="row-top">
          <text class="t-title">{{ m.title }}</text>
          <text v-if="m.isRead === 0" class="dot">未读</text>
        </view>
        <text class="sub">{{ m.content || '（无内容）' }}</text>
        <text class="sub">{{ m.createTime }}</text>
      </view>
      <view v-if="!messages.length" class="empty">暂无消息</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listMessagesApi, markReadApi, markAllReadApi, getToken } from '@/api'

const messages = ref([])

onShow(async () => {
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  loadData()
})

async function loadData() {
  try {
    const data = await listMessagesApi({ pageNum: 1, pageSize: 20 })
    messages.value = (data && data.records) || []
  } catch (e) {}
}

async function readOne(m) {
  if (m.isRead !== 0) return
  await markReadApi(m.id)
  loadData()
}

async function readAll() {
  await markAllReadApi()
  uni.showToast({ title: '已全部标记已读', icon: 'success' })
  loadData()
}
</script>

<style scoped>
.head-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.link { color: #409eff; font-size: 26rpx; }
.msg-item {
  border-bottom: 1rpx solid #f0f0f0;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.msg-item:last-child { border-bottom: none; }
.msg-item.unread { background: #f0f7ff; margin: 0 -24rpx; padding: 20rpx 24rpx; }
.row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-title { font-size: 28rpx; font-weight: 500; flex: 1; }
.dot { color: #f56c6c; font-size: 22rpx; }
</style>
