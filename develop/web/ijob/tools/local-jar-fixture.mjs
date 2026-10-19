import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import process from 'node:process'
export function prepareLocalJar(directory, javaHome) {
  const root = join(directory, 'local-jar-artifacts'),
    classes = join(directory, 'fixture-classes')
  mkdirSync(root, { recursive: true })
  mkdirSync(classes)
  const source = join(directory, 'EditorFixture.java'),
    manifest = join(directory, 'fixture-manifest.mf')
  // Publication validates this class without initializing it or executing run().
  writeFileSync(
    source,
    'package sample.localjar; import com.ifielsc.ijob.spi.localjar.*; public final class EditorFixture implements LocalJarTask { public EditorFixture() {} public LocalJarResult run(LocalJarExecution execution, LocalJarContext context) { throw new AssertionError("Publication must never execute this fixture"); } }',
  )
  writeFileSync(
    manifest,
    'Manifest-Version: 1.0\nIJob-Plugin-Api-Version: 1\nImplementation-Version: editor-fixture-1.0\nIJob-Plugin-Class: sample.localjar.EditorFixture\n\n',
  )
  const run = (command, args) => {
    const result = spawnSync(
      join(javaHome, 'bin', command + (process.platform === 'win32' ? '.exe' : '')),
      args,
      { windowsHide: true, encoding: 'utf8' },
    )
    if (result.status !== 0) throw Error('Local JAR fixture compilation failed: ' + result.stderr)
  }
  run('javac', [
    '-cp',
    resolve(
      process.env.IJOB_TEST_PLUGIN_SPI_JAR ||
        '../../api/ijob/ijob-plugin-spi/target/ijob-plugin-spi-1.0.0.jar',
    ),
    '-d',
    classes,
    source,
  ])
  const jar = join(root, 'editor-fixture.jar')
  run('jar', ['--create', '--file', jar, '--manifest', manifest, '-C', classes, '.'])
  writeFileSync(
    join(directory, 'local-jar-fixture.json'),
    JSON.stringify({
      jarFile: 'editor-fixture.jar',
      sha256: createHash('sha256').update(readFileSync(jar)).digest('hex'),
      entryClass: 'sample.localjar.EditorFixture',
      jarSize: readFileSync(jar).length,
    }),
  )
  return root
}
