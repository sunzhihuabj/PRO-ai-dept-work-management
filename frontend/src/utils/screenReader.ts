/**
 * E1 眼睛 · 读屏摘要收集（V2.0）
 *
 * 业务页面在关键区域（统计卡/表格/表单）标注 `data-ai-readable` 属性，
 * AI 读屏按钮调用 collectScreenDigest() 将当前屏幕内容结构化为文本摘要，
 * 注入对话上下文，让 AI"看得见"用户正在看的页面。
 *
 * 形状约定：<div data-ai-readable="区域标题"> … </div>，
 * 属性值可选（区域标题），缺省用元素 aria-label / 前置标题文本。
 */

const MAX_REGIONS = 8
const MAX_CHARS_PER_REGION = 600
const MAX_TOTAL_CHARS = 3600

/** 折叠空白 */
function squeeze(text: string): string {
  return text.replace(/\s+/g, ' ').trim()
}

/** 推断区域标题：属性值 → aria-label → 内部首个 heading/label 文本 */
function regionTitle(el: HTMLElement): string {
  const explicit = el.getAttribute('data-ai-readable')
  if (explicit && explicit !== 'true' && explicit !== '') return explicit
  const aria = el.getAttribute('aria-label')
  if (aria) return aria
  const head = el.querySelector('h1,h2,h3,h4,.el-card__header,.region-title')
  return head ? squeeze(head.textContent || '') : '未命名区域'
}

/** 收集当前页面全部可读区域摘要 */
export function collectScreenDigest(): string {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-ai-readable]'))
  if (!nodes.length) return ''

  const parts: string[] = []
  let total = 0
  for (const el of nodes.slice(0, MAX_REGIONS)) {
    // 跳过不可见区域
    if (el.offsetParent === null) continue
    const raw = squeeze(el.innerText || '')
    if (!raw) continue
    let text = raw.length > MAX_CHARS_PER_REGION ? raw.slice(0, MAX_CHARS_PER_REGION) + '…' : raw
    if (total + text.length > MAX_TOTAL_CHARS) {
      text = text.slice(0, Math.max(0, MAX_TOTAL_CHARS - total)) + '…'
    }
    parts.push(`【${regionTitle(el)}】${text}`)
    total += text.length
    if (total >= MAX_TOTAL_CHARS) break
  }
  return parts.join('\n')
}
