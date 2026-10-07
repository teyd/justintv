import { defineConfig } from 'vite-plus';

export default defineConfig({
  lint: {
    ignorePatterns: ['android/**', 'ios/**', '.expo/**', 'env.d.ts'],
    options: {
      typeAware: true,
      typeCheck: true,
    },
  },
  fmt: {
    singleQuote: true,
    ignorePatterns: ['android/**', 'ios/**', '.expo/**', 'env.d.ts'],
  },
  staged: {
    '*': 'vp check --fix',
  },
});
