import { ref } from 'vue';

// Shared (module-level) dark-mode state. Persisted to localStorage; defaults to the
// OS preference on first load. Drives the `.dark` class on <html>, which the Tailwind
// `dark:` custom-variant (see style.css) keys off of.
const STORAGE_KEY = 'sms-archive-theme';
const isDark = ref(false);

function apply() {
  document.documentElement.classList.toggle('dark', isDark.value);
  // Keep mobile browser chrome in sync with the page background (gray-50 / gray-900)
  document.querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', isDark.value ? '#111827' : '#f9fafb');
}

/** Call once before mount to set the initial theme without a flash. */
export function initDarkMode() {
  const stored = localStorage.getItem(STORAGE_KEY);
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  isDark.value = stored ? stored === 'dark' : prefersDark;
  apply();
}

export function useDarkMode() {
  function toggle() {
    isDark.value = !isDark.value;
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light');
    apply();
  }
  return { isDark, toggle };
}
