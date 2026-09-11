import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/aids',
    name: 'aids',
    component: () => import('../views/AidsView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/campus',
    name: 'campus',
    component: () => import('../views/CampusView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/aids/create',
    name: 'aid-create',
    component: () => import('../views/AidCreateView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/aids/:id',
    name: 'aid-detail',
    component: () => import('../views/AidDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/community',
    name: 'community',
    component: () => import('../views/CommunityView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/plaza',
    name: 'plaza',
    component: () => import('../views/PlazaView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/ProfileEditView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    redirect: { name: 'home' }
  },
  {
    path: '/users/:id',
    name: 'user-home',
    component: () => import('../views/UserHomeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLogin && (to.name === 'login' || to.name === 'register')) {
      return { name: 'home' }
    }
    return true
  }
  if (to.meta.requiresAuth && !auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles?.length) {
    const role = auth.user?.role
    if (!to.meta.roles.includes(role)) {
      return { name: 'home' }
    }
  }
  return true
})

export default router
