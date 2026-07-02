import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import { createPinia } from 'pinia';
import './style.css'
import { useAuthStore } from './stores/authStore';
import { initDarkMode } from './composables/useDarkMode';

// Apply the saved/OS theme before mount to avoid a flash of the wrong theme.
initDarkMode();

const app = createApp(App);
app.use(createPinia());
app.use(PrimeVue, { theme: { preset: Aura } });
app.use(ToastService);
app.use(ConfirmationService);
app.use(router);

app.mount("#app");

// hydrate user if token present
const authStore = useAuthStore();
if (authStore.accessToken) { authStore.fetchMe(); }
