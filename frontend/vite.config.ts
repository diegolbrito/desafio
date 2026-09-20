/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/shared/test/setup.ts',
    globals: true,
    css: true,
    // pool "forks" (padrao) trava esperando resposta do worker dentro do
    // container Docker usado para build (sem systemd/init completo, sem
    // Maven aqui - ver PROGRESS.md); "threads" evita spawn de processo via
    // child_process/IPC e funciona de forma confiavel nesse ambiente.
    pool: 'threads',
  },
})
