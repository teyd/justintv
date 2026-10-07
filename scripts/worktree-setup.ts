import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

/**
 * Worktree setup beyond `bun install`: gitignored local files do not exist in a new
 * worktree, so copy the local env over from the primary checkout. Run it from the
 * worktree root (`bun scripts/worktree-setup.ts`); T3 Code runs it automatically.
 */

const ENV_FILE = '.env.local';

function gitOutput(cwd: string, args: string[]): string | null {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8' });

  if (result.status !== 0) return null;

  return result.stdout.trim();
}

function main(): void {
  const root = gitOutput(process.cwd(), ['rev-parse', '--show-toplevel']);

  if (root === null) {
    console.warn('[setup] not inside a git repository; skipping local env copy');

    return;
  }

  const commonDir = gitOutput(root, ['rev-parse', '--git-common-dir']);

  if (commonDir === null) {
    console.warn('[setup] could not resolve the git common directory; skipping local env copy');

    return;
  }

  const primaryRoot = dirname(resolve(root, commonDir));

  if (primaryRoot === root) {
    console.log('[setup] primary checkout; nothing to copy');

    return;
  }

  const source = join(primaryRoot, ENV_FILE);
  const target = join(root, ENV_FILE);

  if (!existsSync(source)) {
    console.log(`[setup] no ${ENV_FILE} in ${primaryRoot}; skipping`);

    return;
  }

  if (existsSync(target)) {
    console.log(`[setup] ${ENV_FILE} already present`);

    return;
  }

  copyFileSync(source, target);
  console.log(`[setup] copied ${ENV_FILE} from ${primaryRoot}`);
}

main();
