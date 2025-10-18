import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Search from "../views/Search.vue";
import Messages from "../views/Messages.vue";
import Gallery from "../views/Gallery.vue";
import Dashboard from "../views/Dashboard.vue";
import Import from "../views/Import.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Dashboard", component: Dashboard },
    { path: "/search", name: "Search", component: Search },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/messages", name: "Messages", component: Messages },
    { path: "/import", name: "Import", component: Import },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
