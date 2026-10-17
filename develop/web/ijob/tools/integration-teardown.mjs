import process from 'node:process'
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve, dirname, basename } from 'node:path'

// Windows may terminate the server process tree before Node exit hooks can run.
export default function teardown() {
  if (process.env.IJOB_TEST_SQL_FIXTURE !== 'true' || !process.env.IJOB_TEST_DIRECTORY) return
  const directory = resolve(process.env.IJOB_TEST_DIRECTORY)
  if (
    dirname(directory) !== resolve(tmpdir()) ||
    !basename(directory).startsWith('ijob-frontend-security-')
  )
    throw new Error('Refusing to stop an unexpected PostgreSQL directory')
  const data = join(directory, 'postgresql')
  if (!existsSync(join(data, 'postmaster.pid'))) return
  const pgBin = process.env.IJOB_TEST_PG_BIN
  if (!pgBin) throw new Error('Set IJOB_TEST_PG_BIN to PostgreSQL binaries')
  const binary = join(pgBin, process.platform === 'win32' ? 'pg_ctl.exe' : 'pg_ctl')
  const result = spawnSync(binary, ['-D', data, '-m', 'immediate', '-w', 'stop'], {
    windowsHide: true,
    stdio: 'ignore',
    timeout: 15000,
  })
  if (result.status !== 0 && existsSync(join(data, 'postmaster.pid')))
    throw new Error('Temporary PostgreSQL fixture did not stop')
}
