import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' }
      },
      {
        path: 'task',
        name: 'TaskList',
        component: () => import('@/views/task/TaskList.vue'),
        meta: { title: '任务管理', icon: 'Document' }
      },
      {
        path: 'task/detail/:id',
        name: 'TaskDetail',
        component: () => import('@/views/task/TaskDetail.vue'),
        meta: { title: '任务详情', hidden: true }
      },
      {
        path: 'project',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectList.vue'),
        meta: { title: '项目管理', icon: 'Folder' }
      },
      {
        path: 'project/detail/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetail.vue'),
        meta: { title: '项目详情', hidden: true }
      },
      {
        path: 'system/user',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'file/upload',
        name: 'FileUpload',
        component: () => import('@/views/file/Upload.vue'),
        meta: { title: '文件上传', icon: 'Upload' }
      },
      {
        path: 'file/list',
        name: 'FileList',
        component: () => import('@/views/file/List.vue'),
        meta: { title: '文件列表', icon: 'Document' }
      },
      {
        path: 'file/detail/:id',
        name: 'FileDetail',
        component: () => import('@/views/file/Detail.vue'),
        meta: { title: '文件详情', hidden: true }
      },
      {
        path: 'file/categories',
        name: 'FileCategories',
        component: () => import('@/views/file/CategoryManage.vue'),
        meta: { title: '文件分类管理', hidden: true }
      },
      {
        path: 'event',
        name: 'EventList',
        component: () => import('@/views/event/EventList.vue'),
        meta: { title: '事件审批', icon: 'Bell' }
      },
      {
        path: 'event/detail/:id',
        name: 'EventDetail',
        component: () => import('@/views/event/EventDetail.vue'),
        meta: { title: '事件详情', hidden: true }
      },
      {
        path: 'kpi',
        name: 'KpiManage',
        component: () => import('@/views/kpi/KpiManage.vue'),
        meta: { title: '绩效管理', icon: 'DataAnalysis' }
      },
      {
        path: 'ai',
        name: 'AiAssistant',
        component: () => import('@/views/ai/AiAssistant.vue'),
        meta: { title: 'AI助手', icon: 'MagicStick' }
      },
      {
        path: 'message',
        name: 'MessageCenter',
        component: () => import('@/views/message/MessageCenter.vue'),
        meta: { title: '消息中心', icon: 'Message' }
      },
      {
        path: 'system/dept',
        name: 'DeptManagement',
        component: () => import('@/views/system/DeptManagement.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding' }
      },
      {
        path: 'system/log',
        name: 'OperLog',
        component: () => import('@/views/system/OperLog.vue'),
        meta: { title: '操作日志', icon: 'Tickets' }
      },
      {
        path: 'system/notice',
        name: 'NoticeManage',
        component: () => import('@/views/system/NoticeManage.vue'),
        meta: { title: '通知公告', icon: 'BellFilled' }
      },
      {
        path: 'system/ai-config',
        name: 'AiConfig',
        component: () => import('@/views/system/AiConfig.vue'),
        meta: { title: 'AI 服务配置', icon: 'MagicStick' }
      },
      {
        path: 'agent',
        name: 'AgentWorkbench',
        component: () => import('@/views/agent/AgentWorkbench.vue'),
        meta: { title: 'Agent 工作台', icon: 'ChatDotRound' }
      },
      {
        path: 'contract',
        name: 'ContractManagement',
        component: () => import('@/views/contract/ContractManagement.vue'),
        meta: { title: '合同管理', icon: 'Stamp' }
      },
      {
        path: 'design',
        name: 'DesignManagement',
        component: () => import('@/views/design/DesignManagement.vue'),
        meta: { title: '设计管理', icon: 'SetUp' }
      },
      {
        path: 'workshop',
        name: 'WorkshopManagement',
        component: () => import('@/views/workshop/WorkshopManagement.vue'),
        meta: { title: '样机管理', icon: 'Box' }
      },
      {
        path: 'delivery',
        name: 'DeliveryManagement',
        component: () => import('@/views/delivery/DeliveryManagement.vue'),
        meta: { title: '交付管理', icon: 'Van' }
      },
      {
        path: 'tender',
        name: 'TenderManagement',
        component: () => import('@/views/tender/TenderManagement.vue'),
        meta: { title: '投标管理', icon: 'Promotion' }
      },
      {
        path: 'ecn',
        name: 'EcnManagement',
        component: () => import('@/views/phase3/EcnManagement.vue'),
        meta: { title: '变更管理', icon: 'RefreshRight' }
      },
      {
        path: 'cost',
        name: 'CostManagement',
        component: () => import('@/views/phase3/CostManagement.vue'),
        meta: { title: '成本管理', icon: 'Coin' }
      },
      {
        path: 'aftermarket',
        name: 'AftermarketManagement',
        component: () => import('@/views/phase3/AftermarketManagement.vue'),
        meta: { title: '售后管理', icon: 'Service' }
      },
      {
        path: 'archive',
        name: 'ArchiveManagement',
        component: () => import('@/views/phase3/ArchiveManagement.vue'),
        meta: { title: '档案管理', icon: 'Collection' }
      },
      {
        path: 'bonus',
        name: 'BonusManage',
        component: () => import('@/views/bonus/BonusManage.vue'),
        meta: { title: '奖金管理', icon: 'Money' }
      },
      {
        path: 'lean',
        name: 'LeanManage',
        component: () => import('@/views/lean/LeanManage.vue'),
        meta: { title: '精益改进', icon: 'Aim' }
      },
      {
        path: 'lean/a3',
        name: 'A3Manage',
        component: () => import('@/views/lean/A3Manage.vue'),
        meta: { title: 'A3报告', hidden: true }
      },
      {
        path: 'lean/stream',
        name: 'ValueStream',
        component: () => import('@/views/lean/ValueStream.vue'),
        meta: { title: '价值流分析', icon: 'DataLine' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

export default router
