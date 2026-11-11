import type { Directive } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * v-perm 按钮级权限指令（T-404）
 * 用法：<el-button v-perm="'btn:system/user:create'">新建</el-button>
 * 或：<el-button v-perm="['btn:system/user:create','btn:system/user:update']">任一满足即显示</el-button>
 * 规则：未启用精细控制（finePermEnabled=false）时全部显示（灰度原则）；
 *      启用后按 finePermissions 判断，无权限则移除元素。
 */
export const perm: Directive = {
  mounted(el: HTMLElement, binding: { value: string | string[] }) {
    check(el, binding)
  },
  updated(el: HTMLElement, binding: { value: string | string[] }) {
    check(el, binding)
  }
}

function check(el: HTMLElement, binding: { value: string | string[] }) {
  const userStore = useUserStore()
  const required: string[] = Array.isArray(binding.value) ? binding.value : [binding.value]
  if (!required.length) return

  // 灰度：未启用精细控制时不限制
  if (!userStore.finePermEnabled) {
    el.style.display = ''
    return
  }
  const allowed = required.some((code: string) => userStore.hasPerm(code))
  if (!allowed) {
    el.style.display = 'none'
  } else {
    el.style.display = ''
  }
}
