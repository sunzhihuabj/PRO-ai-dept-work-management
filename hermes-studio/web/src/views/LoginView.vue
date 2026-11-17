<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useRuntimeStore } from '../stores/runtime'

const auth = useAuthStore()
const runtime = useRuntimeStore()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function submit() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    runtime.refreshHealth()
    runtime.refreshCapabilities()
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-brand">
        <span class="logo-dot"></span>
        <h1>Hermes Studio</h1>
        <p>Hermes Agent 全功能 Web 控制台 · API Server 8642</p>
      </div>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" data-testid="login-user">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password data-testid="login-pass" @keyup.enter="submit">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" data-testid="login-btn" @click="submit">
          进入控制台
        </el-button>
      </el-form>
      <p class="muted" style="text-align: center; margin-top: 16px">
        账号在 server/.env（STUDIO_USER / STUDIO_PASS）中配置 · 默认 admin
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d2530 0%, #2b3a52 60%, #409eff22 100%);
}
.login-box {
  width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 36px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.login-brand { text-align: center; margin-bottom: 24px; }
.login-brand h1 { margin: 8px 0 4px; font-size: 22px; }
.login-brand p { margin: 0; color: #909399; font-size: 12px; }
.logo-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; background: #ffd700; box-shadow: 0 0 10px #ffd700; }
</style>
