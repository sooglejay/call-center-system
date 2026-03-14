import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.test.ts'],
    coverage: {
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'dist/',
        'src/**/*.test.ts',
        'src/scripts/',
        'src/config/database.memory.ts'
      ]
    },
    testTimeout: 10000,
    setupFiles: ['./src/tests/setup.ts']
  },
  resolve: {
    alias: {
      '@': './src'
    }
  }
});
