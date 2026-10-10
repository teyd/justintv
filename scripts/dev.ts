import { spawn, spawnSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { createServer } from 'node:net';
import { basename, join, resolve } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';

/**
 * Worktree-aware Expo dev launcher.
 *
 * Every React Native debug build dials `localhost:8081` on the device, which makes
 * parallel worktrees fight over one port. This script gives each worktree its own
 * stable Metro port and remaps `adb reverse tcp:8081` per device onto it, so the
 * installed app always talks to the worktree that started the server.
 */

/** Port a plain React Native debug build dials on the device; `adb reverse` maps it to the worktree port. */
const APP_PORT = 8081;

const PORT_RANGE_START = 8100;

const PORT_RANGE_SIZE = 700;

const PORT_FILE = '.expo/dev-port';

const EXPO_BIN = 'node_modules/.bin/expo';

const EXPO_SERVER = /\bexpo\s+(start|run:android)\b/;

type Command = 'start' | 'android' | 'stop';

type DevOptions = {
  command: Command;
  clear: boolean;
  devices: string[];
  port: number | null;
  reverse: boolean;
  passthrough: string[];
};

type Device = { serial: string };

function parseArgs(argv: string[]): DevOptions {
  const [first = 'start', ...tail] = argv;

  const options: DevOptions = {
    command: 'start',
    clear: false,
    devices: [],
    port: null,
    reverse: true,
    passthrough: [],
  };

  let rest = tail;

  if (first === 'android' || first === 'stop' || first === 'start') {
    options.command = first;
  } else if (first.startsWith('-')) {
    // `bun run dev --device X` hands flags to the script before any command word.
    rest = argv;
  } else {
    throw new Error(`Unknown command "${first}". Use start, android or stop.`);
  }

  for (let index = 0; index < rest.length; index++) {
    const arg = rest[index];

    if (arg === undefined) continue;

    if (arg === '--') continue; // `bun run` separator; everything after it is already ours.

    if (arg === '--clear' || arg === '-c') {
      options.clear = true;
      continue;
    }

    if (arg === '--no-reverse') {
      options.reverse = false;
      continue;
    }

    if (arg === '--device') {
      const value = rest[index + 1];

      if (value === undefined) throw new Error('--device needs a serial from `adb devices`.');

      options.devices.push(value);
      index++;
      continue;
    }

    if (arg === '--port') {
      const value = rest[index + 1];
      const port = Number.parseInt(value ?? '', 10);

      if (value === undefined || Number.isNaN(port)) throw new Error('--port needs a number.');

      options.port = port;
      index++;
      continue;
    }

    options.passthrough.push(arg);
  }

  return options;
}

function gitOutput(cwd: string, args: string[]): string | null {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8' });

  if (result.status !== 0) return null;

  return result.stdout.trim();
}

function repoRoot(): string {
  return gitOutput(process.cwd(), ['rev-parse', '--show-toplevel']) ?? process.cwd();
}

/** FNV-1a keeps the port stable for a worktree path across runs and machines. */
function fnv1a(input: string): number {
  let hash = 0x811c9dc5;

  for (let index = 0; index < input.length; index++) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }

  return hash >>> 0;
}

function preferredPort(root: string): number {
  return PORT_RANGE_START + (fnv1a(root) % PORT_RANGE_SIZE);
}

function isPortFree(port: number): Promise<boolean> {
  return new Promise((resolvePromise) => {
    const probe = createServer();

    probe.once('error', () => resolvePromise(false));
    probe.once('listening', () => {
      probe.close(() => resolvePromise(true));
    });
    probe.listen(port);
  });
}

async function resolvePort(root: string, requested: number | null): Promise<number> {
  if (requested !== null) {
    if (!(await isPortFree(requested))) throw new Error(`Port ${requested} is already in use.`);

    return requested;
  }

  const start = preferredPort(root);

  for (let offset = 0; offset < PORT_RANGE_SIZE; offset++) {
    const candidate = PORT_RANGE_START + ((start - PORT_RANGE_START + offset) % PORT_RANGE_SIZE);

    if (await isPortFree(candidate)) return candidate;
  }

  throw new Error(
    `No free dev-server port in ${PORT_RANGE_START}..${PORT_RANGE_START + PORT_RANGE_SIZE - 1}.`,
  );
}

function isExpoServerProcess(pid: number, root: string): boolean {
  try {
    if (resolve(readlinkSync(`/proc/${pid}/cwd`)) !== root) return false;

    const command = readFileSync(`/proc/${pid}/cmdline`, 'utf8').replaceAll('\0', ' ').trim();
    const [firstToken = ''] = command.split(' ');
    const launcher = basename(firstToken);

    if (launcher !== 'node' && launcher !== 'bun') return false;

    return command.includes(EXPO_BIN) || EXPO_SERVER.test(command);
  } catch {
    return false;
  }
}

/** Finds Expo/Metro processes whose working directory is this worktree, never another checkout's. */
function listServerPids(root: string): number[] {
  if (!existsSync('/proc')) return [];

  const pids: number[] = [];

  for (const entry of readdirSync('/proc')) {
    const pid = Number.parseInt(entry, 10);

    if (Number.isNaN(pid) || pid === process.pid) continue;

    if (isExpoServerProcess(pid, root)) pids.push(pid);
  }

  return pids;
}

function terminate(pid: number, signal: 'SIGTERM' | 'SIGKILL'): void {
  try {
    process.kill(pid, signal);
  } catch {
    // Already exited.
  }
}

function isAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);

    return true;
  } catch {
    return false;
  }
}

async function stopWorktreeServers(root: string): Promise<number> {
  const pids = listServerPids(root);

  if (pids.length === 0) return 0;

  for (const pid of pids) terminate(pid, 'SIGTERM');
  await sleep(700);

  for (const pid of pids.filter(isAlive)) terminate(pid, 'SIGKILL');
  await sleep(100);

  return pids.length;
}

function attachedDevices(): Device[] {
  const result = spawnSync('adb', ['devices'], { encoding: 'utf8' });

  if (result.error !== undefined || result.status !== 0) return [];

  const devices: Device[] = [];

  for (const line of result.stdout.split('\n').slice(1)) {
    const [serial, state] = line.trim().split(/\s+/);

    if (serial !== undefined && serial.length > 0 && state === 'device') devices.push({ serial });
  }

  return devices;
}

function selectDevices(attached: Device[], requested: string[]): Device[] {
  if (requested.length === 0) return attached.length === 1 ? attached : [];

  return attached.filter((device) => requested.includes(device.serial));
}

function warnMissingDevices(attached: Device[], requested: string[]): void {
  if (requested.length === 0) return;

  const known = new Set(attached.map((device) => device.serial));
  const missing = requested.filter((serial) => !known.has(serial));

  if (missing.length > 0) {
    const serials = attached.map((device) => device.serial).join(', ') || 'none';

    console.warn(`[dev] no attached device matches ${missing.join(', ')} (attached: ${serials})`);
  }
}

function setReverse(serial: string, hostPort: number): boolean {
  const result = spawnSync('adb', ['-s', serial, 'reverse', `tcp:${APP_PORT}`, `tcp:${hostPort}`], {
    encoding: 'utf8',
  });

  return result.status === 0;
}

function clearReverse(serial: string, hostPort: number): void {
  const listed = spawnSync('adb', ['-s', serial, 'reverse', '--list'], { encoding: 'utf8' });

  if (listed.status !== 0) return;

  for (const line of listed.stdout.split('\n')) {
    const parts = line.trim().split(/\s+/);
    const remote = parts[1];
    const local = parts[2];

    if (remote === `tcp:${APP_PORT}` && local === `tcp:${hostPort}`) {
      spawnSync('adb', ['-s', serial, 'reverse', '--remove', remote], { encoding: 'utf8' });
    }
  }
}

function readPortFile(root: string): number | null {
  try {
    const port = Number.parseInt(readFileSync(join(root, PORT_FILE), 'utf8').trim(), 10);

    return Number.isNaN(port) ? null : port;
  } catch {
    return null;
  }
}

function writePortFile(root: string, port: number): void {
  mkdirSync(join(root, '.expo'), { recursive: true });
  writeFileSync(join(root, PORT_FILE), `${port}\n`);
}

function removePortFile(root: string): void {
  rmSync(join(root, PORT_FILE), { force: true });
}

function expoBin(root: string): string {
  const bin = join(root, EXPO_BIN);

  if (!existsSync(bin)) throw new Error(`Expo CLI not found at ${bin}. Run \`bun install\` first.`);

  return bin;
}

function runExpo(root: string, args: string[]): Promise<number> {
  return new Promise((resolvePromise) => {
    const child = spawn(expoBin(root), args, { cwd: root, stdio: 'inherit' });

    process.once('SIGINT', () => child.kill('SIGINT'));
    process.once('SIGTERM', () => child.kill('SIGTERM'));

    child.on('error', (error) => {
      console.error(`[dev] failed to start Expo: ${error.message}`);
      resolvePromise(1);
    });
    child.on('exit', (code, signal) => resolvePromise(code ?? (signal === null ? 1 : 0)));
  });
}

async function reverseForDevices(
  port: number,
  requested: string[],
  enabled: boolean,
): Promise<number> {
  if (!enabled) return 0;

  const attached = attachedDevices();
  const selected = selectDevices(attached, requested);

  warnMissingDevices(attached, requested);

  for (const device of selected) {
    if (setReverse(device.serial, port)) {
      console.log(`[dev] ${device.serial}: device :${APP_PORT} -> host :${port}`);
    } else {
      console.warn(`[dev] ${device.serial}: adb reverse failed; is the device online?`);
    }
  }

  if (attached.length > 1 && requested.length === 0) {
    const serials = attached.map((device) => device.serial).join(', ');

    console.log(
      `[dev] multiple devices attached (${serials}); pass --device <serial> to point one here`,
    );
  }

  return selected.length;
}

async function reportStopped(root: string): Promise<boolean> {
  const stopped = await stopWorktreeServers(root);

  if (stopped > 0) {
    console.log(
      `[dev] stopped ${stopped} stale dev server process${stopped === 1 ? '' : 'es'} for this worktree`,
    );

    return true;
  }

  return false;
}

async function runStart(root: string, options: DevOptions): Promise<number> {
  await reportStopped(root);

  const port = await resolvePort(root, options.port);

  console.log(`[dev] worktree ${root}`);
  console.log(`[dev] Metro :${port}`);
  const reversed = await reverseForDevices(port, options.devices, options.reverse);

  if (reversed > 0)
    console.log('[dev] reload the app (press r here, or reload from the dev menu) to attach it');

  writePortFile(root, port);

  const args = [
    'start',
    '--port',
    String(port),
    ...(options.clear ? ['--clear'] : []),
    ...options.passthrough,
  ];

  return runExpo(root, args);
}

async function runAndroid(root: string, options: DevOptions): Promise<number> {
  await reportStopped(root);

  const port = await resolvePort(root, options.port);
  const attached = attachedDevices();

  if (attached.length === 0) {
    throw new Error(
      'No Android device or emulator detected. Start one, then retry (`adb devices` shows none).',
    );
  }

  if (options.devices.length === 0 && attached.length > 1) {
    const serials = attached.map((device) => device.serial).join(', ');

    throw new Error(`Multiple devices attached (${serials}). Pass --device <serial>.`);
  }

  const requestedSerial = options.devices.at(0);

  const target =
    requestedSerial === undefined
      ? attached.at(0)
      : attached.find((device) => device.serial === requestedSerial);

  if (target === undefined) {
    const serials = attached.map((device) => device.serial).join(', ');

    throw new Error(`Device "${requestedSerial}" is not attached (attached: ${serials}).`);
  }

  if (options.reverse && setReverse(target.serial, port)) {
    console.log(`[dev] ${target.serial}: device :${APP_PORT} -> host :${port}`);
  }

  if (options.clear)
    console.warn('[dev] --clear has no effect on run:android; use --no-build-cache instead');

  writePortFile(root, port);

  const args = [
    'run:android',
    '--port',
    String(port),
    '--device',
    target.serial,
    ...options.passthrough,
  ];

  return runExpo(root, args);
}

async function runStop(root: string): Promise<number> {
  const port = readPortFile(root);
  const stopped = await reportStopped(root);

  if (!stopped) console.log('[dev] no dev servers running for this worktree');

  if (port !== null) {
    for (const device of attachedDevices()) clearReverse(device.serial, port);
  }

  removePortFile(root);

  return 0;
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const root = repoRoot();

  if (options.command === 'stop') {
    process.exitCode = await runStop(root);

    return;
  }

  process.exitCode =
    options.command === 'android' ? await runAndroid(root, options) : await runStart(root, options);
}

main().catch((error) => {
  console.error(`[dev] ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
});
