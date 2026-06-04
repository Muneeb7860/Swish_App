import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'remoteRider',
      filename: 'remoteEntry.js',
      exposes: {
        './RiderDashboard': './src/RiderDashboard.jsx',
      },
      shared: ['react', 'react-dom']
    })
  ],
  server: {
    port: 5001,
    cors: true
  },
  preview: {
    port: 5001,
    cors: true
  },
  build: {
    target: 'esnext',
    minify: false,
    cssCodeSplit: false
  }
})
