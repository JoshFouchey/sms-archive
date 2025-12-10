import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Search from "../views/Search.vue";
import Messages from "../views/Messages.vue";
import MessagesHistorical from "../views/MessagesHistorical.vue";
import Gallery from "../views/Gallery.vue";
import Dashboard from "../views/Dashboard.vue";
import Import from "../views/Import.vue";
import Login from "../views/Login.vue";
import Register from "../views/Register.vue";
import Contacts from "../views/Contacts.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Dashboard", component: Dashboard },
    { path: "/search", name: "Search", component: Search },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/messages", redirect: "/messages-historical" },
    { path: "/messages-old", name: "MessagesOld", component: Messages },
    { path: "/messages-historical", name: "MessagesHistorical", component: MessagesHistorical },
    { path: "/messages-historical/:id", name: "messages", component: MessagesHistorical },
    { path: "/import", name: "Import", component: Import },
    { path: "/login", name: "Login", component: Login },
    { path: "/register", name: "Register", component: Register },
    { path: "/contacts", name: "Contacts", component: Contacts },
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
