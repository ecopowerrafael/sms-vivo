import { defineConfig } from 'vitest/config';
export default defineConfig({ test: { environment: 'jsdom', globals: true, pool: 'forks', minWorkers: 1, maxWorkers: 1 } });
