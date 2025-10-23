import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import ToastService from 'primevue/toastservice';
import { createPinia } from 'pinia';
import './style.css'
import { useAuthStore } from './stores/authStore';

const app = createApp(App);
app.use(createPinia());
app.use(PrimeVue, { theme: { preset: Aura } });
app.use(ToastService);
app.use(router);

app.mount("#app");

// hydrate user if token present
const authStore = useAuthStore();
if (authStore.accessToken) { authStore.fetchMe(); }
