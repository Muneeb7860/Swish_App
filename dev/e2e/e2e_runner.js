import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import net from 'net';

const WORKSPACE_DIR = 'C:/Users/DELL 9420/Documents/swiss_App';
const LOGS_DIR = path.join(WORKSPACE_DIR, 'dev/e2e');

const processes = [];

// Helper to check if a port is open
function isPortOpen(port) {
  return new Promise((resolve) => {
    const checkAddress = (host) => {
      const socket = new net.Socket();
      const onError = () => {
        socket.destroy();
        if (host === '127.0.0.1') {
          // If IPv4 failed, fall back to checking IPv6 loopback
          checkAddress('::1');
        } else {
          resolve(false);
        }
      };
      socket.setTimeout(500);
      socket.once('error', onError);
      socket.once('timeout', onError);
      socket.connect(port, host, () => {
        socket.end();
        resolve(true);
      });
    };
    checkAddress('127.0.0.1');
  });
}

// Helper to wait for all required ports
async function waitForPorts(ports, timeoutMs = 60000) {
  const start = Date.now();
  console.log(`[E2E RUNNER] Waiting for ports: ${ports.join(', ')}...`);
  while (Date.now() - start < timeoutMs) {
    const statuses = await Promise.all(ports.map(port => isPortOpen(port)));
    if (statuses.every(status => status === true)) {
      console.log('[E2E RUNNER] All services are online!');
      return true;
    }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  return false;
}

// Helper to spawn background processes
function runService(name, command, args, cwd, logFile) {
  console.log(`[E2E RUNNER] Starting service [${name}] in directory: ${cwd}...`);
  const logStream = fs.createWriteStream(logFile);
  
  // On Windows, use shell: true to resolve command shortcuts like mvn and npm
  const proc = spawn(command, args, { cwd, shell: true });
  proc.stdout.pipe(logStream);
  proc.stderr.pipe(logStream);
  
  proc.on('close', (code) => {
    console.log(`[E2E RUNNER] Service [${name}] exited with code: ${code}`);
  });
  
  processes.push(proc);
  return proc;
}

async function cleanUp() {
  console.log('[E2E RUNNER] Cleaning up background services...');
  
  // Terminate all processes
  for (const proc of processes) {
    if (proc && !proc.killed) {
      try {
        // On Windows, kill process tree utilizing taskkill
        if (process.platform === 'win32') {
          spawn('taskkill', ['/pid', proc.pid, '/f', '/t']);
        } else {
          proc.kill('SIGTERM');
        }
      } catch (err) {
        console.error(`[E2E RUNNER] Error killing process ${proc.pid}:`, err.message);
      }
    }
  }
  console.log('[E2E RUNNER] Services terminated.');
}

async function run() {
  // Ensure log directory exists
  if (!fs.existsSync(LOGS_DIR)) {
    fs.mkdirSync(LOGS_DIR, { recursive: true });
  }

  // Clear previous log files
  ['backend.log', 'bff.log', 'host.log', 'customer.log', 'rider.log', 'admin.log'].forEach(file => {
    const logPath = path.join(LOGS_DIR, file);
    if (fs.existsSync(logPath)) {
      fs.writeFileSync(logPath, '');
    }
  });

  try {
    // 1. Boot Backend Monolith
    runService(
      'Backend',
      '"C:/Users/DELL 9420/Documents/swiss_App/backend/apache-maven-3.9.6/bin/mvn.cmd"',
      ['spring-boot:run'],
      path.join(WORKSPACE_DIR, 'backend'),
      path.join(LOGS_DIR, 'backend.log')
    );

    // 2. Boot BFF Cloud Gateway
    runService(
      'BFF Gateway',
      '"C:/Users/DELL 9420/Documents/swiss_App/backend/apache-maven-3.9.6/bin/mvn.cmd"',
      ['spring-boot:run'],
      path.join(WORKSPACE_DIR, 'bff'),
      path.join(LOGS_DIR, 'bff.log')
    );

    // 3. Boot frontend App Shell Host
    runService(
      'Host App Shell',
      'npm',
      ['run', 'dev'],
      path.join(WORKSPACE_DIR, 'frontend-host'),
      path.join(LOGS_DIR, 'host.log')
    );

    // 4. Boot Customer remote MFE
    runService(
      'Customer Remote',
      'npm',
      ['run', 'preview', '--', '--port', '3001', '--strictPort'],
      path.join(WORKSPACE_DIR, 'frontend-customer'),
      path.join(LOGS_DIR, 'customer.log')
    );

    // 5. Boot Rider remote MFE
    runService(
      'Rider Remote',
      'npm',
      ['run', 'preview', '--', '--port', '3002', '--strictPort'],
      path.join(WORKSPACE_DIR, 'frontend-rider'),
      path.join(LOGS_DIR, 'rider.log')
    );

    // 6. Boot Admin remote MFE
    runService(
      'Admin Remote',
      'npm',
      ['run', 'preview', '--', '--port', '3003', '--strictPort'],
      path.join(WORKSPACE_DIR, 'frontend-admin'),
      path.join(LOGS_DIR, 'admin.log')
    );

    // 7. Wait for all target ports to be open
    const online = await waitForPorts([8080, 8081, 3000, 3001, 3002, 3003], 120000);
    if (!online) {
      throw new Error('Timeout: Not all ports came online in time. Check the log files in dev/e2e.');
    }

    // Allow 3 additional seconds to ensure application context loads completely
    await new Promise(resolve => setTimeout(resolve, 3000));

    // 8. Run Playwright script
    console.log('[E2E RUNNER] Spawning Playwright E2E execution tests...');
    const runner = spawn('node', ['e2e_test.js'], { cwd: LOGS_DIR, shell: true, stdio: 'inherit' });

    await new Promise((resolve, reject) => {
      runner.on('close', (code) => {
        if (code === 0) {
          console.log('[E2E RUNNER] Playwright E2E execution finished successfully!');
          resolve();
        } else {
          reject(new Error(`Playwright E2E exited with non-zero exit code: ${code}`));
        }
      });
    });

  } catch (err) {
    console.error('[E2E RUNNER] Execution failed:', err);
  } finally {
    await cleanUp();
    process.exit(0);
  }
}

run();
