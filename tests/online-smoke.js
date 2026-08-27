const assert = require('assert/strict');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const root = path.resolve(__dirname, '..');
const port = 31973;
const blueprint = fs.readFileSync(path.join(root, 'render.yaml'), 'utf8');
assert.match(blueprint, /plan:\s+free/);
assert.match(blueprint, /key:\s+DATABASE_URL\s+sync:\s+false/);
assert.doesNotMatch(blueprint, /^databases:/m);

const child = spawn(process.execPath, ['-r', './tests/mock-pg.js', 'server-online.js'], {
  cwd:root,
  env:{
    ...process.env,
    DATABASE_URL:'postgresql://localhost/mock',
    ENCRYPTION_KEY:'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    PORT:String(port),
    NODE_ENV:'test'
  },
  stdio:['ignore', 'pipe', 'pipe']
});

let output = '';
child.stdout.on('data', chunk => { output += chunk; });
child.stderr.on('data', chunk => { output += chunk; });

async function waitForServer() {
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`线上服务提前退出：\n${output}`);
    try {
      const response = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (response.ok) return;
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error(`线上服务启动超时：\n${output}`);
}

(async () => {
  try {
    await waitForServer();
    const health = await fetch(`http://127.0.0.1:${port}/healthz`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { ok:true });

    const home = await fetch(`http://127.0.0.1:${port}/`);
    const html = await home.text();
    assert.equal(home.status, 200);
    assert.match(html, /auth\.css/);
    assert.match(html, /auth\.js/);
    assert.match(home.headers.get('content-security-policy'), /frame-ancestors 'none'/);
    const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)].map(match => match[1]);
    for (const asset of assets) {
      const response = await fetch(`http://127.0.0.1:${port}/${asset}`);
      assert.equal(response.status, 200, `missing public asset: ${asset}`);
    }

    const session = await fetch(`http://127.0.0.1:${port}/api/auth/session`);
    assert.equal(session.status, 401);

    const privateSource = await fetch(`http://127.0.0.1:${port}/server-online.js`);
    assert.equal(privateSource.status, 404);

    console.log('online smoke test passed');
  } finally {
    child.kill('SIGTERM');
  }
})().catch(error => {
  console.error(error);
  child.kill('SIGTERM');
  process.exitCode = 1;
});
