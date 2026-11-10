<template>
  <view class="page">
    <view class="stats card">
      <view class="stat">
        <text class="num">{{ stats.taskStats ? stats.taskStats.total : 0 }}</text>
        <text class="lbl">任务总数</text>
      </view>
      <view class="stat">
        <text class="num warn">{{ stats.taskStats ? stats.taskStats.inProgress : 0 }}</text>
        <text class="lbl">进行中</text>
      </view>
      <view class="stat">
        <text class="num danger">{{ stats.taskStats ? stats.taskStats.overdue : 0 }}</text>
        <text class="lbl">已逾期</text>
      </view>
      <view class="stat">
        <text class="num ok">{{ stats.taskStats ? stats.taskStats.completed : 0 }}</text>
        <text class="lbl">已完成</text>
      </view>
    </view>

    <view class="card">
      <view class="head-row">
        <text class="title">我的待办</text>
        <text class="sub">共 {{ todoList.length }} 项</text>
      </view>
      <view v-for="t in todoList" :key="t.id" class="todo-item" @click="goDetail(t.id)">
        <view class="todo-top">
          <text class="todo-title">{{ t.taskName }}</text>
          <text class="tag" :class="pTag(t.priority)">{{ pText(t.priority) }}</text>
        </view>
        <view class="todo-bottom">
          <text class="sub">{{ statusText(t.status) }}</text>
          <text class="sub">截止 {{ t.deadline || '-' }}</text>
        </view>
      </view>
      <view v-if="!todoList.length" class="empty">暂无待办，休息一下吧</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getStatsApi, getMyTodoApi, getToken, getUserInfo } from '@/api'

const stats = ref({})
const todoList = ref([])

const pText = (p) => p === 1 ? '高' : p === 3 ? '低' : '中'
const pTag = (p) => p === 1 ? 'tag-danger' : p === 3 ? 'tag-info' : 'tag-warning'
const statusText = (s) => ['待办', '进行中', '待审核', '已完成', '已关闭'][s] || '未知'
const goDetail = (id) => uni.navigateTo({ url: '/pages/task/detail?id=' + id })

onShow(async () => {
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  try {
    const [s, todo] = await Promise.all([getStatsApi(), getMyTodoApi()])
    stats.value = s || {}
    todoList.value = todo || []
  } catch (e) {}
})
</script>

<style scoped>
.page { padding-bottom: 40rpx; }
.stats {
  display: flex;
  justify-content: space-around;
}
.stat { text-align: center; }
.stat .num { display: block; font-size: 40rpx; font-weight: bold; color: #303133; }
.stat .num.warn { color: #e6a23c; }
.stat .num.danger { color: #f56c6c; }
.stat .num.ok { color: #67c23a; }
.stat .lbl { font-size: 22rpx; color: #909399; }
.head-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.todo-item {
  border-bottom: 1rpx solid #f0f0f0;
  padding: 20rpx 0;
}
.todo-item:last-child { border-bottom: none; }
.todo-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.todo-title { font-size: 28rpx; font-weight: 500; flex: 1; }
.todo-bottom {
  display: flex;
  justify-content: space-between;
  margin-top: 8rpx;
}
</style>
