/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
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
