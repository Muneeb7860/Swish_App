import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'admin',
      filename: 'remoteEntry.js',
      exposes: {
        './AdminPanel': './src/components/AdminPanel.jsx',
        './BusinessApp': './src/components/BusinessApp.jsx',
        './InventoryApp': './src/components/InventoryApp.jsx',
        './SystemEngineRoom': './src/components/SystemEngineRoom.jsx'
      },
      shared: ['react', 'react-dom']
    })
  ],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  },
  server: {
    port: 3003,
    cors: true
  }
});
