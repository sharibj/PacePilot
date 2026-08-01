import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `API_URL` is the dev-proxy target (docker-compose sets it to http://backend:8080).
const apiTarget = process.env.API_URL || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
    },
  },
});
