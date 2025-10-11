import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import ToastService from 'primevue/toastservice';
import './style.css'

const app = createApp(App);

app.use(PrimeVue, { theme: { preset: Aura } });
app.use(ToastService);
app.use(router);

app.mount("#app");
