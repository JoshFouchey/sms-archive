import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';
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
// Align PrimeVue's primary with the app's blue brand (Aura defaults to emerald),
// and tie its dark theme to our .dark class (default follows the OS setting,
// which would disagree with the in-app toggle).
const AppPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}', 100: '{blue.100}', 200: '{blue.200}', 300: '{blue.300}',
      400: '{blue.400}', 500: '{blue.500}', 600: '{blue.600}', 700: '{blue.700}',
      800: '{blue.800}', 900: '{blue.900}', 950: '{blue.950}',
    },
  },
});
app.use(PrimeVue, { theme: { preset: AppPreset, options: { darkModeSelector: '.dark' } } });
app.use(ToastService);
app.use(ConfirmationService);
app.use(router);

app.mount("#app");

// hydrate user if token present
const authStore = useAuthStore();
if (authStore.accessToken) { authStore.fetchMe(); }
