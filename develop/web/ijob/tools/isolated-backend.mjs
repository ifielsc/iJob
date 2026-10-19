// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { prepareLocalJar } from './local-jar-fixture.mjs'
import { spawn, spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import process from 'node:process'
import console from 'node:console'
import { mkdtempSync, existsSync, readdirSync, writeFileSync, unlinkSync } from 'node:fs'
import { tmpdir, homedir } from 'node:os'
import { resolve, join } from 'node:path'
const javaHome = process.env.IJOB_TEST_JAVA_HOME || process.env.JAVA_HOME
if (!javaHome) throw new Error('Set IJOB_TEST_JAVA_HOME or JAVA_HOME to a JDK 21 installation')
const java = join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
const artifact = resolve(
  process.env.IJOB_TEST_BACKEND_JAR || '../../api/ijob/ijob-starter/target/ijob-starter-1.0.0.jar',
)
const cryptoRoot = join(
  homedir(),
  '.m2/repository/org/springframework/security/spring-security-crypto',
)
const crypto =
  process.env.IJOB_TEST_CRYPTO_JAR ||
  readdirSync(cryptoRoot)
    .sort()
    .reverse()
    .map((v) => join(cryptoRoot, v, `spring-security-crypto-${v}.jar`))
    .find(existsSync)
if (!process.env.IJOB_TEST_PASSWORD || !existsSync(artifact) || !crypto)
  throw new Error('Build the backend jar and provide the isolated test password first.')
const hash = spawnSync(java, ['--class-path', crypto, 'tools/TestPasswordHash.java'], {
  encoding: 'utf8',
  windowsHide: true,
})
if (hash.status !== 0 || !hash.stdout.startsWith('$2a$'))
  throw new Error('Unable to prepare isolated account hash')
const directory =
  process.env.IJOB_TEST_DIRECTORY || mkdtempSync(join(tmpdir(), 'ijob-frontend-security-'))
// Only the integration test configuration opts into this disposable SQL target.
let sqlFixture = false
if (process.env.IJOB_TEST_SQL_FIXTURE === 'true') {
  const pgBin = process.env.IJOB_TEST_PG_BIN
  if (!pgBin) throw new Error('Set IJOB_TEST_PG_BIN to PostgreSQL binaries')
  const binary = (name) => join(pgBin, name + (process.platform === 'win32' ? '.exe' : ''))
  for (const name of ['initdb', 'pg_ctl', 'psql'])
    if (!existsSync(binary(name))) throw new Error('Set IJOB_TEST_PG_BIN to PostgreSQL binaries')
  const pgData = join(directory, 'postgresql')
  const pwFile = join(directory, 'fixture-password')
  const run = (name, args, options = {}) => {
    const result = spawnSync(binary(name), args, {
      windowsHide: true,
      encoding: 'utf8',
      timeout: 45000,
      ...options,
    })
    if (result.status !== 0) throw new Error('Isolated PostgreSQL fixture failed: ' + name)
  }
  writeFileSync(pwFile, process.env.IJOB_TEST_PASSWORD, { mode: 0o600 })
  try {
    run('initdb', [
      '-D',
      pgData,
      '-U',
      'fixture',
      '--auth=scram-sha-256',
      '--pwfile=' + pwFile,
      '--encoding=UTF8',
      '--locale=C',
    ])
  } finally {
    unlinkSync(pwFile)
  }
  // pg_ctl targets only this mkdtemp-owned cluster, never an installed service.
  process.on('exit', () => {
    spawnSync(binary('pg_ctl'), ['-D', pgData, '-m', 'immediate', '-w', 'stop'], {
      windowsHide: true,
      stdio: 'ignore',
      timeout: 15000,
    })
  })
  run('pg_ctl', [
    '-D',
    pgData,
    '-l',
    join(directory, 'postgresql.log'),
    '-o',
    '-h 127.0.0.1 -p 15438',
    '-w',
    'start',
  ])
  run(
    'psql',
    [
      '-h',
      '127.0.0.1',
      '-p',
      '15438',
      '-U',
      'fixture',
      '-d',
      'postgres',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      'CREATE TABLE demo_orders(id INTEGER PRIMARY KEY); INSERT INTO demo_orders VALUES (1), (2);',
    ],
    { env: { ...process.env, PGPASSWORD: process.env.IJOB_TEST_PASSWORD } },
  )
  sqlFixture = true
}
const port = Number(process.env.IJOB_TEST_BACKEND_PORT ?? 18086)
if (!Number.isInteger(port) || port < 1 || port > 65535)
  throw new Error('Invalid test backend port')
const settings = {
  'server.address': '127.0.0.1',
  'server.port': port,
  'spring.profiles.active': 'frontend-integration',
  'spring.config.location': 'classpath:/application.yml',
  'spring.config.import': 'optional:classpath:database/sqlite.yml',
  'spring.datasource.url': `jdbc:sqlite:${join(directory, 'isolated.db').replaceAll('\\', '/')}?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE`,
  'spring.datasource.hikari.minimum-idle': 0,
  'spring.flyway.enabled': true,
  'spring.flyway.locations': 'classpath:db/release/sqlite',
  'app.platform-database.vendor': 'SQLITE',
  'app.runtime.mode': 'STANDALONE',
  'app.runtime.instance-name': 'frontend-isolated',
  'app.runtime.roles': sqlFixture ? 'API,WORKER' : 'API',
  'app.auth.mode': 'DATABASE',
  'app.auth.bootstrap-enabled': true,
  'app.auth.username': 'ui-test-admin',
  'app.auth.password-hash': hash.stdout.trim(),
  'app.security.aes.key': randomBytes(32).toString('base64'),
  'app.security.aes.key-id': 'frontend-isolated',
  'app.schedule.enabled': false,
  'app.event.recovery.enabled': false,
  'app.event.ingress.enabled': false,
  'app.retention.enabled': false,
  'app.notification.outbox.enabled': false,
  'app.plugin.local-jar.enabled': process.env.IJOB_TEST_LOCAL_JAR === 'true',
  ...(process.env.IJOB_TEST_LOCAL_JAR === 'true'
    ? { 'app.plugin.local-jar.root-directory': prepareLocalJar(directory, javaHome) }
    : {}),
  'app.plugin.server-monitor.enabled': process.env.IJOB_TEST_MONITOR === 'true',
  'app.plugin.etl.enabled': process.env.IJOB_TEST_ETL === 'true',
  'app.plugin.batch-etl.enabled': process.env.IJOB_TEST_BATCH_ETL === 'true',
  'spring.jmx.enabled': false,
  'logging.level.root': 'WARN',
  'spring.main.banner-mode': 'off',
}
// Never load local profile configuration; the only datastore is a fresh OS temporary directory.
const child = spawn(java, ['-jar', artifact], {
  env: { ...process.env, SPRING_APPLICATION_JSON: JSON.stringify(settings) },
  stdio: ['ignore', 'inherit', 'inherit'],
  windowsHide: true,
})
console.log(`Isolated SQLite backend PID ${child.pid}; data directory ${directory}`)
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill())
process.on('exit', () => child.kill())
child.on('exit', (code) => process.exit(code ?? 0))
