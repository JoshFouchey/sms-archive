import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

// Lazy-loaded route components — each becomes a separate chunk
const Ask = () => import("../views/Ask.vue");
const Messages = () => import("../views/Messages.vue");
const Gallery = () => import("../views/Gallery.vue");
const Contacts = () => import("../views/Contacts.vue");
const Admin = () => import("../views/Admin.vue");
const Login = () => import("../views/Login.vue");
const Register = () => import("../views/Register.vue");

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Ask", component: Ask },
    { path: "/messages", name: "Messages", component: Messages },
    { path: "/messages/:id", name: "MessagesDetail", component: Messages },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/contacts", name: "Contacts", component: Contacts },
    { path: "/admin", name: "Admin", component: Admin },
    { path: "/login", name: "Login", component: Login },
    { path: "/register", name: "Register", component: Register },
    // Redirects for old routes
    { path: "/search", redirect: "/" },
    { path: "/explore", redirect: "/" },
    { path: "/ai-settings", redirect: "/admin" },
    { path: "/import", redirect: "/admin" },
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
