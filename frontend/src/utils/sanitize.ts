import DOMPurify from 'dompurify'
import { marked } from 'marked'

/**
 * Markdown 统一渲染入口（H3 存储型 XSS 整改）
 *
 * 所有 AI 回复 / Agent 结果的 v-html 渲染必须经本函数收口：
 * marked 解析后由 DOMPurify 白名单净化——移除 script/iframe/svg onload、
 * javascript: 协议、无引号事件属性等全部向量（替代旧正则净化，杜绝绕过）。
 */
export function renderMarkdown(src: string): string {
  if (!src) return ''
  const html = marked.parse(src, { async: false, breaks: true }) as string
  return DOMPurify.sanitize(html, {
    FORBID_TAGS: ['style', 'form', 'input', 'button', 'textarea'],
    FORBID_ATTR: ['target']
  })
}
