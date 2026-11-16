<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>部门工作管理平台</h1>
        <p>基于AI的部门工作管理软件</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password size="large" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.rememberMe">记住我（7天内免登录）</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">
        <p>本地开发种子账号（仅限开发环境；上线部署后必须修改并移除本提示）：</p>
        <p>sunzhihua / 123456（部门负责人）</p>
        <p>lihailong / 123456（项目负责人）</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await userStore.login({
        username: form.username,
        password: form.password,
        rememberMe: form.rememberMe
      })
      // M5 弱密码治理：账号仍在使用初始弱密码（123456）时强制引导改密
      if ((res?.data as any)?.mustChangePassword) {
        ElMessageBox.alert(
          '您的账号仍在使用初始弱密码，存在被冒用的安全风险。<br>请登录后点击页面右上角头像 →「修改密码」，设置不少于8位且含字母和数字的新密码。',
          '安全提示：请尽快修改初始密码',
          { confirmButtonText: '知道了', dangerouslyUseHTMLString: true }
        ).catch(() => {})
      }
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    } catch (error) {
      // 错误已在拦截器处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: min(420px, calc(100vw - 32px));
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);

  .login-header {
    text-align: center;
    margin-bottom: 32px;
    h1 { font-size: 24px; color: #303133; margin: 0 0 8px; }
    p { color: #767676; font-size: 14px; margin: 0; }
  }

  .login-btn { width: 100%; }

  .login-tip {
    margin-top: 16px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    font-size: 12px;
    color: #767676;
    p { margin: 4px 0; }
  }
}
</style>
