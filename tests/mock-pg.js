const pg = require('pg');

class MockPool {
  async query() { return { rows:[] }; }
  async end() {}
}

pg.Pool = MockPool;
