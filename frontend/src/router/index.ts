import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import Home from "../views/Home.vue";
import Search from "../views/Search.vue";
import Messages from "../views/Messages.vue";
import Gallery from "../views/Gallery.vue";

const routes: Array<RouteRecordRaw> = [
    { path: "/", name: "Home", component: Home },
    { path: "/search", name: "Search", component: Search },
    { path: "/gallery", name: "Gallery", component: Gallery },
    { path: "/messages", name: "Messages", component: Messages },
];


const router = createRouter({
    history: createWebHistory(),
    routes,
});



export default router;
