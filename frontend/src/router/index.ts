import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Search from "../views/Search.vue";
import Messages from "../views/Messages.vue";
import Gallery from "../views/Gallery.vue";
import Dashboard from "../views/Dashboard.vue";
import Import from "../views/Import.vue";
import Login from "../views/Login.vue";
import Register from "../views/Register.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Dashboard", component: Dashboard },
    { path: "/search", name: "Search", component: Search },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/messages", name: "Messages", component: Messages },
    { path: "/import", name: "Import", component: Import },
    { path: "/login", name: "Login", component: Login },
    { path: "/register", name: "Register", component: Register },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

import { useAuthStore } from '../stores/authStore';
router.beforeEach((to, _from, next) => {
  const store = useAuthStore();
  const publicPaths = ['/login','/register'];
  if (!store.accessToken && !publicPaths.includes(to.path)) {
    return next('/login');
  }
  next();
});

export default router;
