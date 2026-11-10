<template>
  <view class="page">
    <view class="card">
      <view class="profile">
        <text class="avatar">{{ (user && user.realName ? user.realName : '未')[0] }}</text>
        <view class="p-body">
          <text class="p-name">{{ user ? user.realName : '未登录' }}</text>
          <text class="sub">{{ user ? user.username : '' }}</text>
        </view>
      </view>
      <view class="roles" v-if="user && user.roles && user.roles.length">
        <text v-for="r in user.roles" :key="r" class="tag tag-primary">{{ roleText(r) }}</text>
      </view>
    </view>

    <view class="card menu-card">
      <view class="menu-item" @click="goMessage">
        <text>消息中心</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="switchAccount">
        <text>切换账号</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <button class="btn-logout" @click="doLogout">退出登录</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getToken, getUserInfo, logout } from '@/utils/request'

const user = ref(null)

const roleText = (r) => ({
  ROLE_DEPT_LEADER: '部门负责人',
  ROLE_PROJECT_LEADER: '项目负责人',
  ROLE_REFORM_LEADER: '技改负责人',
  ROLE_TECHNICIAN: '技术员',
  ROLE_DEVELOPER: '研发人员',
  ROLE_ARCHIVIST: '档案管理员',
  ROLE_AUDITOR: '审核员'
}[r] || r)

onShow(() => {
  user.value = getUserInfo()
})

const goMessage = () => uni.navigateTo({ url: '/pages/message/message' })
const switchAccount = () => uni.navigateTo({ url: '/pages/login/login' })
const doLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确认退出登录？',
    success: (m) => {
      if (m.confirm) logout()
    }
  })
}
</script>

<style scoped>
.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: #409eff;
  color: #ffffff;
  font-size: 44rpx;
  text-align: center;
  line-height: 110rpx;
}
.p-body { display: flex; flex-direction: column; gap: 6rpx; }
.p-name { font-size: 34rpx; font-weight: bold; }
.roles {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
  flex-wrap: wrap;
}
.menu-card { padding: 0 24rpx; }
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
  font-size: 28rpx;
}
.menu-item:last-child { border-bottom: none; }
.arrow { color: #c0c4cc; }
.btn-logout {
  margin: 40rpx;
  background: #ffffff;
  color: #f56c6c;
  border-radius: 12rpx;
  font-size: 30rpx;
}
</style>
