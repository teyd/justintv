import { defineConfig } from 'vite-plus';

export default defineConfig({
  lint: {
    ignorePatterns: ['android/**', 'ios/**', 'modules/*/android/build/**', '.expo/**', 'env.d.ts'],
    options: {
      typeAware: true,
      typeCheck: true,
    },
  },
  fmt: {
    singleQuote: true,
    ignorePatterns: ['android/**', 'ios/**', 'modules/*/android/build/**', '.expo/**', 'env.d.ts'],
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
  staged: {
    '*': 'vp check --fix',
  },
});
