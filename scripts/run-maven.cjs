const { spawn } = require('node:child_process');
const path = require('node:path');

const args = process.argv.slice(2);
// Use the BE-Java directory (parent of scripts/), not cwd, so this works
// regardless of whether it's invoked via `npm --prefix BE-Java` or from root
const beJavaDir = path.resolve(__dirname, '..');
const wrapper = path.join(beJavaDir, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
const command = process.platform === 'win32' ? (process.env.ComSpec || 'cmd.exe') : wrapper;
const commandArgs = process.platform === 'win32'
  ? ['/d', '/s', '/c', wrapper, ...args]
  : args;

const child = spawn(command, commandArgs, {
  cwd: beJavaDir,
  shell: false,
  stdio: 'inherit',
});

child.on('error', (error) => {
  console.error(`Failed to start Maven wrapper: ${error.message}`);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
