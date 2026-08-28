const pg = require('pg');
const crypto = require('crypto');

const state = { users:[], sessions:[], audit:[], registrationOpen:undefined };
let nextUserId = 1;
let nextAuditId = 1;

if (process.env.MOCK_SEED_ADMIN === 'true') {
  const salt = '0123456789abcdef0123456789abcdef';
  state.users.push({
    id:nextUserId++,
    email:String(process.env.ADMIN_EMAIL || '').toLowerCase(),
    password_salt:salt,
    password_hash:crypto.scryptSync('test-admin-password', salt, 64).toString('hex'),
    is_admin:false,
    disabled_at:null,
    created_at:new Date().toISOString()
  });
}

function rows(value = []) { return { rows:value, rowCount:value.length }; }

class MockPool {
  async query(query, params = []) {
    const sql = String(query).replace(/\s+/g, ' ').trim();

    if (sql.startsWith('UPDATE users SET is_admin=TRUE WHERE email=')) {
      const user = state.users.find(item => item.email === params[0]);
      if (user) user.is_admin = true;
      return rows(user ? [user] : []);
    }
    if (sql.startsWith('INSERT INTO users(')) {
      if (state.users.some(item => item.email === params[0])) {
        const error = new Error('duplicate');
        error.code = '23505';
        throw error;
      }
      const user = { id:nextUserId++, email:params[0], password_salt:params[1], password_hash:params[2], is_admin:false, disabled_at:null, created_at:new Date().toISOString() };
      state.users.push(user);
      return rows([user]);
    }
    if (sql.startsWith('INSERT INTO sessions(')) {
      state.sessions.push({ token_hash:params[0], user_id:Number(params[1]), created_at:new Date().toISOString() });
      return rows();
    }
    if (sql.includes('FROM sessions JOIN users ON users.id = sessions.user_id')) {
      const session = state.sessions.find(item => item.token_hash === params[0]);
      const user = session && state.users.find(item => item.id === session.user_id && !item.disabled_at);
      return rows(user ? [user] : []);
    }
    if (sql === 'SELECT * FROM users WHERE email=$1') return rows(state.users.filter(item => item.email === params[0]));
    if (sql.startsWith('SELECT u.id,u.email,u.is_admin')) {
      if (sql.includes('WHERE u.id=$1')) {
        const user = state.users.find(item => item.id === Number(params[0]));
        return rows(user ? [{ ...user, data:{ applications:[] }, api_url:null, model:null, encrypted_api_key:null, key_last_four:null }] : []);
      }
      return rows(state.users.map(user => ({
        ...user,
        application_count:0,
        event_count:0,
        has_api_key:false,
        last_login_at:state.sessions.find(item => item.user_id === user.id)?.created_at || null
      })));
    }
    if (sql.startsWith('SELECT COUNT(*) AS count FROM sessions')) return rows([{ count:String(state.sessions.length) }]);
    if (sql.startsWith("SELECT value FROM system_settings WHERE key='registration_open'")) return rows(state.registrationOpen === undefined ? [] : [{ value:state.registrationOpen }]);
    if (sql.startsWith('INSERT INTO system_settings(')) {
      state.registrationOpen = Boolean(params[0]);
      return rows();
    }
    if (sql.startsWith('SELECT id,action,target_email,created_at FROM admin_audit_logs')) return rows([...state.audit].reverse());
    if (sql.startsWith('SELECT id,email,is_admin,disabled_at FROM users WHERE id=')) return rows(state.users.filter(item => item.id === Number(params[0])));
    if (sql.startsWith('SELECT id,email,is_admin FROM users WHERE id=')) return rows(state.users.filter(item => item.id === Number(params[0])));
    if (sql.startsWith('UPDATE users SET disabled_at=')) {
      const user = state.users.find(item => item.id === Number(params[0]));
      if (user) user.disabled_at = params[1] ? new Date().toISOString() : null;
      return rows();
    }
    if (sql.startsWith('DELETE FROM sessions WHERE user_id=')) {
      state.sessions = state.sessions.filter(item => item.user_id !== Number(params[0]));
      return rows();
    }
    if (sql.startsWith('DELETE FROM sessions WHERE token_hash=')) {
      state.sessions = state.sessions.filter(item => item.token_hash !== params[0]);
      return rows();
    }
    if (sql.startsWith('INSERT INTO admin_audit_logs(')) {
      state.audit.push({ id:nextAuditId++, action:params[3], target_email:params[2], created_at:new Date().toISOString() });
      return rows();
    }
    if (sql.startsWith('DELETE FROM users WHERE id=')) {
      state.users = state.users.filter(item => item.id !== Number(params[0]));
      return rows();
    }
    return rows();
  }

  async connect() {
    return { query:(query, params) => this.query(query, params), release() {} };
  }

  async end() {}
}

pg.Pool = MockPool;
