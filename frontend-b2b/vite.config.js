import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'remoteB2B',
      filename: 'remoteEntry.js',
      exposes: {
        './B2bDashboard': './src/B2bDashboard.jsx',
      },
      shared: ['react', 'react-dom']
    })
  ],
  server: {
    port: 5002,
    cors: true
  },
  preview: {
    port: 5002,
    cors: true
  },
  build: {
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  }
})
