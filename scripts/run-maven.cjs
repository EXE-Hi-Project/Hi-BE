const { spawn } = require('node:child_process');
const path = require('node:path');

const command = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const args = process.argv.slice(2);
// Use the BE-Java directory (parent of scripts/), not cwd, so this works
// regardless of whether it's invoked via `npm --prefix BE-Java` or from root
const beJavaDir = path.resolve(__dirname, '..');
const child = spawn(command, args, {
  cwd: beJavaDir,
  shell: process.platform === 'win32',
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
