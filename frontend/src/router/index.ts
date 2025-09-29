import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Home from "../views/Home.vue";
import Search from "../views/Search.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", component: Home },
    { path: "/search", component: Search },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
