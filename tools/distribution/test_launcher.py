# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Launcher contract tests with inert commands; real lifecycle rehearsal is separate."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'develop/deployment/first-run'
WINDOWS = os.name == 'nt'


class InstanceLauncherTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='ijob-launcher-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        if WINDOWS:
            hidden = self.root / 'hidden parent'
            hidden.mkdir()
            subprocess.run(['attrib', '+H', str(hidden)], check=True, capture_output=True)
            self.root = hidden
        self.bundle = self.root / 'program space'
        self.bundle.mkdir()
        self.control = self.root / 'control space'
        self.home = self.root / 'data space'
        self.record = self.root / 'calls.txt'
        self.env = os.environ.copy()
        self.env.update(IJOB_HOME=str(self.root / 'unrelated-home'), IJOB_LANG='en',
                        LAUNCHER_TEST_RECORD=str(self.record))
        (self.bundle / 'support').mkdir()
        (self.bundle / 'legacy').mkdir()
        for name in ['ijob.cmd', 'support/instance.ps1', 'ijob.sh']:
            shutil.copy2(SOURCE / name, self.bundle / name)
        for name in ['ijob-setup-tool-1.0.0.jar', 'ijob-starter-1.0.0.jar',
                     'docker-images.lock', 'docker-images.compose.yaml']:
            (self.bundle / name).touch()
        if WINDOWS:
            (self.bundle / 'support/manage.ps1').write_text(
                'param([string]$Action)\n'
                '[IO.File]::AppendAllText($env:LAUNCHER_TEST_RECORD, '
                '"$env:IJOB_HOME|$env:IJOB_LANG|$Action`n")\n'
                'if ($Action -eq "status") { exit 3 }\n', encoding='utf-8')
        else:
            for action in ['start', 'stop', 'status', 'logs', 'start-docker', 'stop-docker']:
                (self.bundle / 'legacy' / (action + '.sh')).write_text(
                    '#!/usr/bin/env bash\n'
                    f'printf \'%s\\n\' "$IJOB_HOME|$IJOB_LANG|{action}|$*" >> "$LAUNCHER_TEST_RECORD"\n'
                    + ('exit 3\n' if action == 'status' else ''), encoding='utf-8')
            bin_dir = self.root / 'bin'
            bin_dir.mkdir()
            docker = bin_dir / 'docker'
            docker.write_text('#!/usr/bin/env bash\nprintf \'%s\\n\' "$IJOB_HOME|$IJOB_LANG|$*" >> "$LAUNCHER_TEST_RECORD"\n')
            docker.chmod(0o700)
            self.env['PATH'] = str(bin_dir) + os.pathsep + self.env['PATH']

    def invoke(self, base, args, expected=0):
        command = (['powershell.exe', '-NoProfile', '-File', str(base / 'support/instance.ps1')]
                   if WINDOWS else ['bash', str(base / 'ijob.sh')])
        result = subprocess.run(command + args, cwd=self.root, env=self.env,
                                capture_output=True, encoding='utf-8', errors='replace', timeout=30)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return result.stdout

    def configure(self, expected=0, mode='linux'):
        args = (['configure', '-Directory', str(self.control), '-Home', str(self.home), '-Lang', 'ZH']
                if WINDOWS else ['configure', '--directory', str(self.control), '--home', str(self.home),
                                 '--lang', 'zh', '--mode', mode])
        return self.invoke(self.bundle, args, expected)

    def test_saved_identity_overrides_ambient_environment_and_preserves_exit_code(self):
        self.configure()
        self.assertFalse(self.home.exists(), 'configure must not initialize application data')
        self.invoke(self.control, ['start'])
        self.invoke(self.control, ['status'], 3)
        self.invoke(self.control, ['logs'])
        self.invoke(self.control, ['restart'])
        self.invoke(self.control, ['stop'])
        lines = self.record.read_text().splitlines()
        self.assertTrue(all(line.startswith(str(self.home) + '|zh|') for line in lines))
        actions = [line.split('|')[2] for line in lines]
        self.assertEqual(['start', 'status', 'logs', 'restart', 'stop'] if WINDOWS else
                         ['start', 'status', 'logs', 'stop', 'start', 'stop'], actions)

    def test_configure_never_overwrites_existing_control_directory(self):
        self.configure()
        original = (self.control / 'instance.conf').read_bytes()
        self.configure(expected=1)
        self.assertEqual(original, (self.control / 'instance.conf').read_bytes())

    def test_missing_duplicate_and_unknown_configuration_fail_before_dispatch(self):
        self.invoke(self.bundle, ['start'], 1)
        self.configure()
        path = self.control / 'instance.conf'
        original = path.read_text()
        for content in [original + 'lang=en\n', original + 'unexpected=value\n',
                        original.replace('lang=zh\n', ''), original.replace('lang=zh', 'lang=ZH')]:
            path.write_text(content, encoding='utf-8', newline='\n')
            self.invoke(self.control, ['start'], 1)
            self.assertFalse(self.record.exists())

    def test_program_control_and_data_cannot_overlap(self):
        self.home = self.bundle / 'data'
        self.configure(expected=1)
        self.assertFalse(self.control.exists())

    def test_logs_once_and_show_work_without_environment_setup(self):
        self.configure()
        self.assertIn('lang=zh', self.invoke(self.control, ['show']))
        (self.home / 'logs').mkdir(parents=True)
        (self.home / 'logs/ijob.log').write_text('synthetic log line\n')
        self.assertIn('synthetic log line', self.invoke(self.control, ['logs', '-Once' if WINDOWS else '--once']))
        self.assertFalse(self.record.exists())

    def test_config_values_are_not_executed_as_shell_code(self):
        self.home = self.root / 'literal $(touch injected) & value'
        self.configure()
        self.invoke(self.control, ['start'])
        self.assertIn(str(self.home), self.record.read_text())
        self.assertFalse((self.root / 'injected').exists())

    def test_platform_specific_mode_dispatch(self):
        if WINDOWS:
            self.configure()
            path = self.control / 'instance.conf'
            path.write_text(path.read_text().replace('mode=windows', 'mode=docker'))
            self.invoke(self.control, ['start'], 1)
            self.assertFalse(self.record.exists())
        else:
            self.configure(mode='docker')
            for args in [['start'], ['restart'], ['status'], ['logs', '--once'], ['stop']]:
                self.invoke(self.control, args)
            lines = self.record.read_text().splitlines()
            self.assertTrue(all(line.startswith(str(self.home) + '|zh|') for line in lines))
            self.assertIn('start-docker|--recreate', lines[1])
            self.assertIn(str(self.home / 'compose.yaml') + ' ps', lines[2])
            self.assertIn('logs --tail 200 backend web', lines[3])



    def test_new_control_can_dispatch_to_old_bundle_layout_for_rollback(self):
        self.configure()
        if WINDOWS:
            (self.bundle / 'support/manage.ps1').rename(self.bundle / 'manage.ps1')
        else:
            for path in (self.bundle / 'legacy').iterdir():
                path.rename(self.bundle / path.name)
            (self.bundle / 'legacy').rmdir()
        self.invoke(self.control, ['start'])
        self.assertIn(str(self.home) + '|zh|start', self.record.read_text())

    def test_relocated_legacy_defaults_remain_beside_program_directory(self):
        for folder in ['legacy', 'support']:
            for path in (SOURCE / folder).iterdir():
                if path.suffix in {'.cmd', '.ps1', '.sh', '.xml'}:
                    raw = path.read_bytes()
                    (self.bundle / folder / path.name).write_bytes(raw.replace(b'\r\n', b'\n') if path.suffix == '.sh' else raw)
        default = self.root / ('ijob-data' if WINDOWS else 'ijob-data-linux')
        default.mkdir()
        (default / 'installation.properties').write_text('mode=' + ('windows' if WINDOWS else 'linux') + '\n')
        (default / 'setup.complete').touch()
        env = self.env.copy()
        env.pop('IJOB_HOME', None)
        env['JAVA_HOME'] = str(self.root / 'unused-java-location')
        args = (['cmd.exe', '/d', '/c', str(self.bundle / 'legacy/status.cmd')] if WINDOWS else
                ['bash', str(self.bundle / 'legacy/status.sh')])
        result = subprocess.run(args, cwd=self.root, env=env, capture_output=True, timeout=30)
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertTrue((default / '.native-control.lock').is_file())
        self.assertFalse((self.bundle / default.name).exists())
        if not WINDOWS:
            result = subprocess.run(['bash', str(self.bundle / 'legacy/stop-docker.sh')],
                                    cwd=self.root, env=env, capture_output=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn(str(self.root / 'ijob-data-docker/compose.yaml'), self.record.read_text())

if __name__ == '__main__':
    unittest.main()
