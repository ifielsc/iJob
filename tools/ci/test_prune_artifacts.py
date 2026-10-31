# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('prune', Path(__file__).with_name('prune-artifacts.py'))
prune = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune)


class RetentionTest(unittest.TestCase):
    def setUp(self):
        self.current = dict(id=20, workflow_id=7, event='workflow_dispatch', head_branch='main',
                            created_at='2026-11-01T01:00:00Z', status='in_progress')
        self.jobs = [dict(name=name, conclusion='success') for name in prune.JOBS]
        self.artifacts = []
        for index, role in enumerate(sorted(prune.ROLES), 100):
            name = f'{role}-20-1' if not role.startswith('frontend') else f'frontend-20-1-{role[-1]}'
            self.artifacts.append(dict(id=index, name=name, expired=False, workflow_run={'id': 20}))
        self.runs = {20: self.current}

    def api(self, path):
        if path.startswith('/actions/artifacts?'):
            return {'artifacts': self.artifacts}
        if '/jobs?' in path:
            return {'jobs': self.jobs}
        return self.runs[int(path.rsplit('/', 1)[1])]

    def add_run(self, run_id, **changes):
        run = dict(self.current, status='completed', created_at='2026-10-31T01:00:00Z')
        run.update(changes)
        self.runs[run_id] = run
        self.artifacts.append(dict(id=run_id, name=f'repository-{run_id}-1', expired=False,
                                   workflow_run={'id': run_id}))

    def test_removes_only_older_completed_same_workflow(self):
        self.add_run(1)
        self.add_run(2, workflow_id=8)
        self.add_run(3, status='in_progress')
        self.add_run(4, created_at='2026-11-02T01:00:00Z')
        keep, targets = prune.plan(self.api, 20)
        self.assertEqual(len(keep), 7)
        self.assertEqual([x['id'] for x in targets], [1])

    def test_failure_or_skipped_job_prevents_deletion(self):
        for conclusion in ['failure', 'skipped', None]:
            self.jobs[0]['conclusion'] = conclusion
            with self.assertRaisesRegex(ValueError, 'seven validation'):
                prune.plan(self.api, 20)

    def test_missing_or_expired_report_prevents_deletion(self):
        self.artifacts[0]['expired'] = True
        with self.assertRaisesRegex(ValueError, 'seven current reports'):
            prune.plan(self.api, 20)
        self.artifacts.pop(0)
        with self.assertRaises(ValueError):
            prune.plan(self.api, 20)

    def test_rerun_retains_latest_report_for_each_role(self):
        duplicate = copy.deepcopy(self.artifacts[0])
        duplicate.update(id=200, name=duplicate['name'].replace('-20-1', '-20-2'))
        self.artifacts.append(duplicate)
        keep, targets = prune.plan(self.api, 20)
        self.assertIn(200, keep)
        self.assertEqual([x['id'] for x in targets], [100])

    def test_push_or_other_branch_cannot_prune(self):
        for changes in [dict(event='push'), dict(head_branch='feature')]:
            original = dict(self.current)
            self.current.update(changes)
            with self.assertRaises(ValueError):
                prune.plan(self.api, 20)
            self.current.update(original)

    def test_pagination_collects_before_any_deletion(self):
        calls = []
        def api(path):
            calls.append(path)
            return {'artifacts': list(range(100)) if path.endswith('&page=1') else [100]}
        self.assertEqual(prune.pages(api, '/actions/artifacts', 'artifacts'), list(range(101)))
        self.assertEqual(len(calls), 2)


if __name__ == '__main__':
    unittest.main()
