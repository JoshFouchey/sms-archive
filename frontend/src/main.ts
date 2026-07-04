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
// darkModeSelector ties PrimeVue's dark theme to our .dark class (default follows the
// OS setting, which would disagree with the in-app toggle).
app.use(PrimeVue, { theme: { preset: Aura, options: { darkModeSelector: '.dark' } } });
app.use(ToastService);
app.use(ConfirmationService);
app.use(router);

app.mount("#app");

// hydrate user if token present
const authStore = useAuthStore();
if (authStore.accessToken) { authStore.fetchMe(); }
