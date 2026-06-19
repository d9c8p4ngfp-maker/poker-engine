import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('./views/HomeView.vue'),
    },
    {
      path: '/create',
      name: 'create',
      component: () => import('./views/CreateRoomView.vue'),
    },
    {
      path: '/room/:roomId',
      name: 'room',
      component: () => import('./views/RoomView.vue'),
    },
  ],
})

export default router
