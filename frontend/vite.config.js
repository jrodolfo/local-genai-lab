import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backendUrl = process.env.BACKEND_URL || `http://localhost:${process.env.BACKEND_PORT || '8080'}`;
const frontendPort = Number(process.env.FRONTEND_PORT || '5173');

export default defineConfig({
  plugins: [react()],
  server: {
    port: frontendPort,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true
      }
    }
  }
});
