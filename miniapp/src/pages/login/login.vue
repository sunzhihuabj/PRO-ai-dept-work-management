<template>
  <view class="login-page">
    <view class="logo-area">
      <text class="logo-title">部门工作管理平台</text>
      <text class="logo-sub">基于AI的部门工作管理软件 · 移动端</text>
    </view>

    <view class="card form-card">
      <view class="field">
        <text class="label">用户名</text>
        <input v-model="username" class="ipt" placeholder="请输入用户名" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="password" class="ipt" password placeholder="请输入密码" />
      </view>
      <button class="btn-primary btn-block" :loading="loading" @click="doLogin">登 录</button>

      <!-- #ifdef MP-WEIXIN -->
      <view class="divider">或</view>
      <button class="btn-wx" :loading="wxLoading" @click="doWxLogin">微信一键登录</button>
      <text class="tip">首次使用将引导绑定账号</text>
      <!-- #endif -->

      <!-- #ifdef H5 -->
      <view class="divider">开发模式</view>
      <button class="btn-wx" :loading="wxLoading" @click="doDevWxLogin">模拟微信登录（dev code）</button>
      <text class="tip">H5 端模拟 wx.login，code 由时间戳生成</text>
      <!-- #endif -->
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { loginApi, wxLoginApi, wxBindApi, setToken, setUserInfo } from '@/api'

const username = ref('sunzhihua')
const password = ref('')
const loading = ref(false)
const wxLoading = ref(false)

function saveSession(data) {
  setToken(data.token)
  setUserInfo(data.user)
  uni.switchTab({ url: '/pages/index/index' })
}

// H5 账密登录
async function doLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入账号密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await loginApi(username.value, password.value)
    saveSession(data)
  } catch (e) {
  } finally {
    loading.value = false
  }
}

// 获取微信 code（条件编译）
function getWxCode() {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (res) => resolve(res.code),
      fail: (err) => reject(err)
    })
    // #endif
    // #ifdef H5
    resolve('dev-' + Date.now())
    // #endif
  })
}

// 微信授权登录：4011 未绑定 → 弹账密绑定
async function doWxLogin() {
  wxLoading.value = true
  try {
    const code = await getWxCode()
    try {
      const data = await wxLoginApi(code)
      saveSession(data)
    } catch (err) {
      if (err && err.code === 4011) {
        // 未绑定 → 引导绑定
        uni.showModal({
          title: '绑定账号',
          editable: true,
          placeholderText: '请输入系统用户名',
          success: async (m1) => {
            if (!m1.confirm || !m1.content) return
            const uname = m1.content
            uni.showModal({
              title: '绑定密码',
              editable: true,
              placeholderText: '请输入系统密码',
              success: async (m2) => {
                if (!m2.confirm || !m2.content) return
                try {
                  const data = await wxBindApi(code, uname, m2.content)
                  uni.showToast({ title: '绑定成功', icon: 'success' })
                  saveSession(data)
                } catch (e2) {}
              }
            })
          }
        })
      }
    }
  } catch (e) {
    uni.showToast({ title: '获取微信登录态失败', icon: 'none' })
  } finally {
    wxLoading.value = false
  }
}

// H5 模拟微信登录
async function doDevWxLogin() {
  wxLoading.value = true
  try {
    const code = await getWxCode()
    try {
      const data = await wxLoginApi(code)
      saveSession(data)
    } catch (err) {
      if (err && err.code === 4011) {
        uni.showToast({ title: '该模拟微信未绑定，请先绑定', icon: 'none' })
      }
    }
  } finally {
    wxLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #409eff 0%, #2b7de9 30%, #f5f6fa 30%);
  padding-top: 160rpx;
}
.logo-area {
  text-align: center;
  margin-bottom: 80rpx;
}
.logo-title {
  display: block;
  color: #ffffff;
  font-size: 44rpx;
  font-weight: bold;
}
.logo-sub {
  display: block;
  color: rgba(255, 255, 255, 0.85);
  font-size: 24rpx;
  margin-top: 12rpx;
}
.form-card {
  margin: 0 40rpx;
  padding: 48rpx 36rpx;
}
.field {
  margin-bottom: 32rpx;
}
.label {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
}
.ipt {
  border: 2rpx solid #dcdfe6;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
}
.divider {
  text-align: center;
  color: #c0c4cc;
  font-size: 24rpx;
  margin: 32rpx 0 20rpx;
}
.btn-wx {
  background: #07c160;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 30rpx;
  width: 100%;
}
.tip {
  display: block;
  text-align: center;
  color: #c0c4cc;
  font-size: 22rpx;
  margin-top: 16rpx;
}
</style>
