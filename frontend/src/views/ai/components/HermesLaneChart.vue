<template>
  <div ref="chartRef" class="lane-chart"></div>
</template>

<script setup lang="ts">
/**
 * Hermes 执行泳道图（V2.0）：把 run 事件渲染为时间轴泳道图。
 * 泳道 = 主流程 + 每个工具一条；横轴 = 相对时间（秒）。
 * 工具调用为矩形块（started→completed 时段，失败红色）；
 * 中间输出/授权请求/终态为主流程泳道上的标记点。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { HermesRunEvent } from '@/api/aiws'

const props = defineProps<{ events: HermesRunEvent[] }>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

/** "yyyy-MM-dd HH:mm:ss" → ms（本地时区） */
function parseTime(t?: string): number {
  if (!t) return NaN
  return new Date(t.replace(' ', 'T')).getTime()
}

function render() {
  if (!chartRef.value) return
  const evs = props.events
  if (!evs.length) return

  if (!chart) chart = echarts.init(chartRef.value)

  // 泳道：主流程 + 工具泳道（按首次出现顺序）
  const toolLanes: string[] = []
  for (const e of evs) {
    if (e.tool && !toolLanes.includes(e.tool)) toolLanes.push(e.tool)
  }
  const lanes = ['主流程', ...toolLanes]
  const laneIdx = (name: string) => lanes.indexOf(name)

  // 时间基准
  const times = evs.map(e => parseTime(e.createTime)).filter(t => !isNaN(t))
  const t0 = times.length ? Math.min(...times) : 0
  const tOf = (e: HermesRunEvent) => {
    const t = parseTime(e.createTime)
    return isNaN(t) ? null : Math.max(0, (t - t0) / 1000)
  }

  // 工具块（started→completed）与主流程标记点
  // 块数据编码：value = [start, width, laneIdx, isErr]
  const blocks: Array<{ value: [number, number, number, number]; tool: string; preview: string }> = []
  const dots: Array<{ value: [number, number]; type: string; preview: string }> = []
  const pendingStart: Record<string, number> = {}
  let lastT = 0

  for (const e of evs) {
    const t = tOf(e)
    if (t == null) continue
    lastT = Math.max(lastT, t)
    if (e.eventType === 'tool.started') {
      pendingStart[e.tool!] = t
    } else if (e.eventType === 'tool.completed') {
      const start = pendingStart[e.tool!] ?? Math.max(0, t - (e.durationMs || 0) / 1000)
      const width = Math.max(t - start, (e.durationMs || 0) / 1000, 1)
      blocks.push({
        value: [start, width, laneIdx(e.tool!), e.isError === 1 ? 1 : 0],
        tool: e.tool!,
        preview: e.preview || ''
      })
      delete pendingStart[e.tool!]
    } else {
      dots.push({ value: [t, 0], type: e.eventType, preview: e.preview || '' })
    }
  }

  const DOT_COLORS: Record<string, string> = {
    'message.interim': '#909399',
    'approval.request': '#e6a23c',
    'run.completed': '#67c23a',
    'run.failed': '#f56c6c',
    'run.cancelled': '#909399',
    'run.interrupted': '#909399',
    'run.steered': '#e6a23c',
    'subagent.start': '#409eff',
    'subagent.complete': '#67c23a'
  }

  chart.setOption({
    grid: { left: 90, right: 20, top: 12, bottom: 28 },
    tooltip: {
      confine: true,
      formatter: (p: any) => {
        const d = p.data || {}
        const tVal = Array.isArray(d.value) ? d.value[0] : 0
        const extra = d.preview ? `<br/>${String(d.preview).slice(0, 120)}` : ''
        return `<b>${d.tool || d.type || p.seriesName}</b><br/>t=${Number(tVal).toFixed(1)}s${extra}`
      }
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: Math.ceil(lastT + 2),
      axisLabel: { formatter: (v: number) => `${v}s`, fontSize: 10 }
    },
    yAxis: {
      type: 'category',
      data: lanes,
      inverse: true,
      axisLabel: {
        fontSize: 11,
        formatter: (v: string) => (v.length > 14 ? v.slice(0, 13) + '…' : v)
      }
    },
    series: [
      {
        name: '工具调用',
        type: 'custom',
        renderItem: (params: any, api: any) => {
          const start = api.value(0)
          const width = api.value(1)
          const lane = api.value(2)
          const err = api.value(3) === 1
          const bandHeight = api.size([0, 1])[1]
          const point = api.coord([start, lane])
          const end = api.coord([start + width, lane])
          const half = bandHeight * 0.22
          return {
            type: 'rect',
            shape: {
              x: point[0], y: point[1] - half,
              width: Math.max(end[0] - point[0], 4), height: half * 2
            },
            style: { fill: err ? '#f56c6c' : '#409eff', opacity: 0.85 }
          }
        },
        data: blocks as any
      },
      {
        name: '流程事件',
        type: 'scatter',
        data: dots.map(d => ({
          ...d,
          itemStyle: { color: DOT_COLORS[d.type] || '#909399' }
        })),
        symbolSize: 12,
        symbol: (_v: any, p: any) => (p?.data?.type === 'approval.request' ? 'diamond' : 'circle')
      }
    ]
  })
}

function resize() { chart?.resize() }

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
watch(() => props.events, () => {
  if (chartRef.value?.offsetParent) render()
}, { deep: true })

/** 容器可见性变化后重绘（父级 collapse 展开时尺寸才就绪） */
function rerender() {
  if (chartRef.value?.offsetParent) render()
}
defineExpose({ rerender })
</script>

<style scoped>
.lane-chart { width: 100%; height: 180px; }
</style>
