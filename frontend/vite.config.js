import path from 'path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Cible surchargeable : le backend local peut démarrer sur un autre
        // port (ex. SERVER_PORT=8081 pour la suite de tests API).
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  // Même proxy pour la prévisualisation du build (npm run preview), sinon
  // les appels vers /api échouent en « Network Error » sur une origine seule.
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        // Découpage du bundle : les libs lourdes (react, axios, framer-motion,
        // chart.js) partent dans des chunks vendors séparés — le chunk
        // principal repasse sous le seuil d'avertissement de 500 kB
        // et le cache navigateur ne re-télécharge que ce qui change.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          motion: ['framer-motion'],
          charts: ['chart.js', 'react-chartjs-2'],
          axios: ['axios'],
          icons: ['lucide-react']
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    }
  }
});