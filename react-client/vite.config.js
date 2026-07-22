import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local dev server proxies /api and /ws to the Spring Boot backend so the app can always use
// same-origin relative paths, exactly like the production nginx config in Dockerfile does.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
});
