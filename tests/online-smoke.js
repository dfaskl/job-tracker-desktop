const assert = require('assert/strict');
const { spawn } = require('child_process');
const vm = require('node:vm');
const path = require('path');
const fs = require('fs');

const root = path.resolve(__dirname, '..');
const port = 31973;
const blueprint = fs.readFileSync(path.join(root, 'render.yaml'), 'utf8');
const authClient = fs.readFileSync(path.join(root, 'auth.js'), 'utf8');
const adminClient = fs.readFileSync(path.join(root, 'admin.js'), 'utf8');
const dedupeClient = fs.readFileSync(path.join(root, 'application-dedupe.js'), 'utf8');
const migrationClient = fs.readFileSync(path.join(root, 'online-migration.js'), 'utf8');
const appClient = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const onlineServer = fs.readFileSync(path.join(root, 'server-online.js'), 'utf8');
const localServer = fs.readFileSync(path.join(root, 'server.js'), 'utf8');
const statsClient = fs.readFileSync(path.join(root, 'stats-v2.js'), 'utf8');
const scheduleClient = fs.readFileSync(path.join(root, 'schedule-calendar.js'), 'utf8');
const commercialClient = fs.readFileSync(path.join(root, 'commercial-polish.js'), 'utf8');
const commercialCss = fs.readFileSync(path.join(root, 'commercial-polish.css'), 'utf8');
const experienceCss = fs.readFileSync(path.join(root, 'experience-polish.css'), 'utf8');
const mobileCss = fs.readFileSync(path.join(root, 'mobile.css'), 'utf8');
assert.match(blueprint, /plan:\s+free/);
assert.match(mobileCss, /@media\(max-width:820px\)/);
assert.match(mobileCss, /grid-template-columns:repeat\(6,minmax\(0,1fr\)\)/);
assert.match(mobileCss, /\.insight-metrics button\{grid-column:auto!important;justify-self:stretch!important/);
assert.match(mobileCss, /--mobile-content-clearance:calc\(88px \+ env\(safe-area-inset-bottom\)\)/);
assert.match(mobileCss, /main\{[^}]*padding:0 12px var\(--mobile-content-clearance\)!important/);
assert.match(mobileCss, /\.modal-mask\{align-items:end;z-index:200/);
assert.match(commercialClient, /id="backupList"/);
assert.match(commercialClient, /loadBackups\(\)/);
assert.match(commercialClient, /applicationCount/);
assert.match(commercialClient, /leftColumn\.appendChild\(apiPanel\)/);
assert.match(commercialClient, /rightColumn\.appendChild\(dataPanel\)/);
assert.doesNotMatch(commercialClient, /渠道亮点/);
assert.match(commercialClient, /\/api\/leaderboard/);
assert.match(adminClient, /data-action="save-profile"/);
assert.match(commercialClient, /rightColumn\.style\.height=matchMedia\('\(min-width:901px\)'\)\.matches/);
assert.match(commercialClient, /new ResizeObserver\(syncColumnHeight\)/);
assert.match(commercialCss, /\.settings-right-column\{grid-template-rows:auto minmax\(0,1fr\);height:100%;min-height:0;overflow:hidden\}/);
assert.match(commercialCss, /\.settings-right-column \.backup-list\{flex:1;max-height:none;min-height:0;overflow-y:auto\}/);
assert.match(mobileCss, /\.event-card\.event-card-range[^}]*grid-template-columns:minmax\(0,1fr\)!important/);
assert.match(mobileCss, /\.schedule-calendar-panel \.delivery-weekdays,\.schedule-calendar-grid\{min-width:0!important/);
assert.match(mobileCss, /\.mail-result-form\{grid-template-columns:minmax\(0,1fr\)!important\}/);
assert.match(authClient, /className = 'mobile-account-button'/);
assert.match(blueprint, /key:\s+DATABASE_URL\s+sync:\s+false/);
assert.match(blueprint, /key:\s+ADMIN_EMAIL\s+sync:\s+false/);
assert.doesNotMatch(blueprint, /^databases:/m);
assert.match(authClient, /if \(nextHtml !== note\.innerHTML\) note\.innerHTML = nextHtml/);
assert.doesNotMatch(authClient, /about-brand p'\)\.forEach\(item => \{ item\.textContent =/);
assert.match(authClient, /class="password-toggle" aria-label="显示密码"/);
assert.match(authClient, /passwordInput\.type = visible \? 'text' : 'password'/);
assert.match(dedupeClient, /if \(!existing && \(!company \|\| !position\)\)/);
assert.match(dedupeClient, /id="mrTimeMode"/);
assert.match(dedupeClient, /id="mrEnds"/);
assert.match(dedupeClient, /结束时间必须晚于开始时间/);
assert.match(dedupeClient, /String\(event\.endsAt \|\| ''\) === endsAt/);
assert.match(dedupeClient, /\.\.\.\(endsAt \? \{ endsAt \} : \{\}\)/);
assert.match(migrationClient, /company-links\.json/);
assert.doesNotMatch(migrationClient, /local-config\.json/);
assert.doesNotMatch(migrationClient, /importLegacyApiConfig/);
assert.doesNotMatch(migrationClient, /importLegacyCompanyLinks/);
assert.doesNotMatch(migrationClient, /legacy-migration-panel/);
assert.doesNotMatch(migrationClient, /data\.settings/);
assert.doesNotMatch(migrationClient, /\/api\/config/);
assert.match(migrationClient, /const isBusinessBackup = Array\.isArray\(data\?\.applications\) && Array\.isArray\(data\?\.events\)/);
assert.match(migrationClient, /const importedLinks = normalizedLinks\(data\)/);
assert.match(migrationClient, /支持线上完整备份、旧版 job-tracker\.json 和 company-links\.json/);
assert.match(migrationClient, /applications:JSON\.parse\(JSON\.stringify\(state\.applications\)\)/);
assert.match(migrationClient, /companyLinks:\[\]/);
assert.match(appClient, /apiUrl:localConfig\.apiUrl/);
assert.match(appClient, /model:localConfig\.model/);
assert.match(onlineServer, /VALUES\(\$1,\$2::jsonb\).*JSON\.stringify\(items\)/);
assert.match(onlineServer, /suggestedStatus、startsAt、endsAt、location/);
assert.match(localServer, /suggestedStatus、startsAt、endsAt、location/);
assert.match(onlineServer, /result\.endsAt = String\(result\.endsAt \|\| ''\)\.trim\(\)/);
assert.doesNotMatch(statsClient, /stageLabels = \[[^\]]*'无消息'/);
assert.doesNotMatch(statsClient, /return '无消息'/);
assert.match(statsClient, /const noMessage=activeApplications\.filter/);
assert.match(appClient, /function eventRecordAt\(event\).*event\.completedAt\|\|event\.endsAt/);
assert.match(appClient, /if\(window\.isOnlineMode&&!window\.currentUser\)return/);
assert.match(appClient, /else\{\s*state=initial\(\);\s*localStorage\.setItem\(KEY,JSON\.stringify\(state\)\)/);
assert.match(appClient, /const TIME_SENSITIVE_REFRESH_MS=60\*1000/);
assert.match(appClient, /setInterval\(refreshTimeSensitiveView,TIME_SENSITIVE_REFRESH_MS\)/);
assert.match(appClient, /window\.addEventListener\('focus',refreshTimeSensitiveView\)/);
assert.match(appClient, /document\.addEventListener\('visibilitychange',refreshTimeSensitiveView\)/);
assert.match(appClient, /日程已到期超过 2 小时/);
assert.match(appClient, /isRange=eventHasRange\(e\)&&!e\.completed/);
assert.match(appClient, /rest=isRange\?timeLabel:timeLabel\.slice\(first\.length\)\.trim\(\)/);
assert.match(appClient, /function resolveEvent\(event,missed=false\).*event\.completedAt=nowText\(\)/);
assert.match(appClient, /else delete event\.completedAt/);
assert.match(appClient, /name="timeMode"/);
assert.match(appClient, /结束时间必须晚于开始时间/);
assert.match(scheduleClient, /eventHasRange\(event\)&&!event\.completed/);
assert.match(scheduleClient, /resolveEvent\(event\)/);
assert.match(scheduleClient, /restoreEvent\(event\)/);
assert.match(experienceCss, /\.home-workbench>\.two-col>\.panel:nth-child\(2\)>\.cards\{grid-auto-rows:max-content\}/);
assert.match(experienceCss, /body:has\(#nav button\[data-page="home"\]\.active\)\{height:auto;overflow-y:auto\}/);
assert.match(experienceCss, /body:has\(#nav button\[data-page="home"\]\.active\) \.home-workbench\{height:auto;display:grid!important;overflow:visible\}/);
assert.match(experienceCss, /\.manual-confirm-panel>\.cards\{flex:none;overflow:visible;padding-right:0\}/);
assert.match(experienceCss, /\.home-workbench \.event-card\.event-card-range\{grid-template-columns:clamp\(220px,16vw,280px\) minmax\(0,1fr\) auto\}/);
assert.match(experienceCss, /\.event-card\.event-card-range:is\(\.is-today,\.is-tomorrow\) \.event-time\{white-space:normal\}/);

const timeContext = { nowText:() => '2026-08-28 14:35' };
const timeFunctions = ['eventHasRange','eventRecordAt','resolveEvent','restoreEvent'].map(name => {
  const match = appClient.match(new RegExp(`^function ${name}.*$`, 'm'));
  assert.ok(match, `missing ${name}`);
  return match[0];
}).join('\n');
vm.runInNewContext(timeFunctions, timeContext);
const pointEvent = { startsAt:'2026-09-01 10:00', completed:false, missed:false };
timeContext.resolveEvent(pointEvent);
assert.equal(pointEvent.completedAt, undefined);
assert.equal(timeContext.eventRecordAt(pointEvent), '2026-09-01 10:00');
const rangeEvent = { startsAt:'2026-09-01 00:00', endsAt:'2026-09-03 18:00', completed:false, missed:false };
assert.equal(timeContext.eventRecordAt(rangeEvent), '2026-09-01 00:00');
timeContext.resolveEvent(rangeEvent);
assert.equal(rangeEvent.completedAt, '2026-08-28 14:35');
assert.equal(timeContext.eventRecordAt(rangeEvent), '2026-08-28 14:35');
timeContext.restoreEvent(rangeEvent);
assert.equal(rangeEvent.completedAt, undefined);
assert.equal(timeContext.eventRecordAt(rangeEvent), '2026-09-01 00:00');

const child = spawn(process.execPath, ['-r', './tests/mock-pg.js', 'server-online.js'], {
  cwd:root,
  env:{
    ...process.env,
    DATABASE_URL:'postgresql://localhost/mock',
    ENCRYPTION_KEY:'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ADMIN_EMAIL:'admin@example.com',
    MOCK_SEED_ADMIN:'true',
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

    const adminPage = await fetch(`http://127.0.0.1:${port}/admin.html`);
    assert.equal(adminPage.status, 200);
    const adminHtml = await adminPage.text();
    assert.match(adminHtml, /admin\.js/);
    for (const asset of ['admin.js', 'admin.css']) {
      const response = await fetch(`http://127.0.0.1:${port}/${asset}`);
      assert.equal(response.status, 200, `missing admin asset: ${asset}`);
    }

    const adminLogin = await fetch(`http://127.0.0.1:${port}/api/auth/login`, {
      method:'POST', headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify({ email:'admin@example.com', password:'test-admin-password' })
    });
    assert.equal(adminLogin.status, 200);
    const adminBody = await adminLogin.json();
    assert.equal(adminBody.user.isAdmin, true);
    const adminCookie = adminLogin.headers.get('set-cookie').split(';')[0];
    const overview = await fetch(`http://127.0.0.1:${port}/api/admin/overview`, { headers:{ Cookie:adminCookie } });
    assert.equal(overview.status, 200);
    const overviewBody = await overview.json();
    assert.equal(overviewBody.currentUser.isAdmin, true);
    const adminDetails = await fetch(`http://127.0.0.1:${port}/api/admin/users/${overviewBody.currentUser.id}/details`, { headers:{ Cookie:adminCookie } });
    assert.equal(adminDetails.status, 200);
    const adminDetailsBody = await adminDetails.json();
    assert.equal('password' in adminDetailsBody, false);
    assert.equal('api' in adminDetailsBody, false);
    assert.deepEqual(adminDetailsBody.applications, []);
    assert.equal(overviewBody.summary.totalUsers, 1);

    const disableSelf = await fetch(`http://127.0.0.1:${port}/api/admin/users/${overviewBody.currentUser.id}`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ disabled:true })
    });
    assert.equal(disableSelf.status, 400);

    const userRegister = await fetch(`http://127.0.0.1:${port}/api/auth/register`, {
      method:'POST', headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify({ email:'user@example.com', password:'another-correct-password' })
    });
    assert.equal(userRegister.status, 201);
    const userBody = await userRegister.json();
    assert.equal(userBody.user.isAdmin, false);
    const userCookie = userRegister.headers.get('set-cookie').split(';')[0];
    const profileUpdate = await fetch(`http://127.0.0.1:${port}/api/admin/users/${userBody.user.id}`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ nickname:'小凡', showOnLeaderboard:true })
    });
    assert.equal(profileUpdate.status, 200);
    const leaderboard = await fetch(`http://127.0.0.1:${port}/api/leaderboard`, { headers:{ Cookie:userCookie } });
    assert.equal(leaderboard.status, 200);
    assert.deepEqual(await leaderboard.json(), { items:[{ nickname:'小凡', applicationCount:0 }] });
    const forbidden = await fetch(`http://127.0.0.1:${port}/api/admin/overview`, { headers:{ Cookie:userCookie } });
    assert.equal(forbidden.status, 403);
    const forbiddenToggle = await fetch(`http://127.0.0.1:${port}/api/admin/settings/registration`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:userCookie },
      body:JSON.stringify({ enabled:false })
    });
    assert.equal(forbiddenToggle.status, 403);

    const disableUser = await fetch(`http://127.0.0.1:${port}/api/admin/users/${userBody.user.id}`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ disabled:true })
    });
    assert.equal(disableUser.status, 200);
    const revokedSession = await fetch(`http://127.0.0.1:${port}/api/auth/session`, { headers:{ Cookie:userCookie } });
    assert.equal(revokedSession.status, 401);

    const enableUser = await fetch(`http://127.0.0.1:${port}/api/admin/users/${userBody.user.id}`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ disabled:false })
    });
    assert.equal(enableUser.status, 200);
    const userLogin = await fetch(`http://127.0.0.1:${port}/api/auth/login`, {
      method:'POST', headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify({ email:'user@example.com', password:'another-correct-password' })
    });
    assert.equal(userLogin.status, 200);

    const wrongDelete = await fetch(`http://127.0.0.1:${port}/api/admin/users/${userBody.user.id}`, {
      method:'DELETE', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ confirmEmail:'wrong@example.com' })
    });
    assert.equal(wrongDelete.status, 400);
    const deleteUser = await fetch(`http://127.0.0.1:${port}/api/admin/users/${userBody.user.id}`, {
      method:'DELETE', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ confirmEmail:'user@example.com' })
    });
    assert.equal(deleteUser.status, 200);
    const finalOverview = await fetch(`http://127.0.0.1:${port}/api/admin/overview`, { headers:{ Cookie:adminCookie } });
    assert.equal((await finalOverview.json()).summary.totalUsers, 1);

    const closeRegistration = await fetch(`http://127.0.0.1:${port}/api/admin/settings/registration`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ enabled:false })
    });
    assert.equal(closeRegistration.status, 200);
    const closedRegister = await fetch(`http://127.0.0.1:${port}/api/auth/register`, {
      method:'POST', headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify({ email:'closed@example.com', password:'registration-is-closed' })
    });
    assert.equal(closedRegister.status, 403);
    const closedOverview = await fetch(`http://127.0.0.1:${port}/api/admin/overview`, { headers:{ Cookie:adminCookie } });
    assert.equal((await closedOverview.json()).summary.registrationOpen, false);

    const openRegistration = await fetch(`http://127.0.0.1:${port}/api/admin/settings/registration`, {
      method:'PATCH', headers:{ 'Content-Type':'application/json', Cookie:adminCookie },
      body:JSON.stringify({ enabled:true })
    });
    assert.equal(openRegistration.status, 200);
    const reopenedRegister = await fetch(`http://127.0.0.1:${port}/api/auth/register`, {
      method:'POST', headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify({ email:'reopened@example.com', password:'registration-is-open' })
    });
    assert.equal(reopenedRegister.status, 201);

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
