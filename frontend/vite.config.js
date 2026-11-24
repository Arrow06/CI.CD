import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // Під час локальної розробки запити /api йдуть на бекенд, запущений на порту 8080
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
