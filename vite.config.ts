import { defineConfig } from 'vite-plus';

export default defineConfig({
  lint: {
    ignorePatterns: [
      'android/**',
      'ios/**',
      'modules/*/android/build/**',
      '.expo/**',
      'env.d.ts',
      '.agents/**',
    ],
    options: {
      typeAware: true,
      typeCheck: true,
    },
  },
  fmt: {
    singleQuote: true,
    ignorePatterns: [
      'android/**',
      'ios/**',
      'modules/*/android/build/**',
      '.expo/**',
      'env.d.ts',
      '.agents/**',
    ],
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
  staged: {
    '*': 'vp check --fix',
  },
});
