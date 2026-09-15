// Re-exports loaded through Vite's SSR module graph so the harness shares the
// exact same Vue / Pinia / vue-router instances as the .vue files under test.
// Importing 'vue' directly from Node would give a second copy and break inject().
export { createSSRApp, reactive, ref, nextTick } from 'vue'
export { renderToString } from '@vue/server-renderer'
export { createPinia, setActivePinia } from 'pinia'
export { createRouter, createMemoryHistory, routeLocationKey } from 'vue-router'
