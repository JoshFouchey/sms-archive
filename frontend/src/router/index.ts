import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Ask from "../views/Ask.vue";
import Messages from "../views/Messages.vue";
import Gallery from "../views/Gallery.vue";
import KnowledgeGraph from "../views/KnowledgeGraph.vue";
import Contacts from "../views/Contacts.vue";
import Admin from "../views/Admin.vue";
import Login from "../views/Login.vue";
import Register from "../views/Register.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Ask", component: Ask },
    { path: "/messages", name: "Messages", component: Messages },
    { path: "/messages/:id", name: "MessagesDetail", component: Messages },
    { path: "/explore", name: "Explore", component: KnowledgeGraph },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/contacts", name: "Contacts", component: Contacts },
    { path: "/admin", name: "Admin", component: Admin },
    { path: "/login", name: "Login", component: Login },
    { path: "/register", name: "Register", component: Register },
    // Redirects for old routes
    { path: "/search", redirect: "/" },
    { path: "/knowledge-graph", redirect: "/explore" },
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
