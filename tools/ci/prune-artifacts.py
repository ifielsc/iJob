# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Retain the newest complete Basic CI evidence without deleting runs or logs."""
import json
import os
import re
import urllib.request

JOBS = {'Repository checks', 'Backend and Agent', 'Docker validation and build',
        *(f'Frontend {shard}/4' for shard in range(1, 5))}
ROLES = {'repository', 'backend', 'docker', *(f'frontend-{shard}' for shard in range(1, 5))}


def pages(api, path, key):
    result = []
    page = 1
    while True:
        data = api(f'{path}?per_page=100&page={page}')[key]
        result.extend(data)
        if len(data) < 100:
            return result
        page += 1


def plan(api, run_id):
    current = api(f'/actions/runs/{run_id}')
    if current['event'] != 'workflow_dispatch' or current['head_branch'] != 'main':
        raise ValueError('Cleanup requires a manually selected main-branch Basic run')
    jobs = pages(api, f'/actions/runs/{run_id}/jobs', 'jobs')
    passed = {job['name'] for job in jobs if job['conclusion'] == 'success'}
    if not JOBS <= passed:
        raise ValueError('All seven validation jobs must succeed before cleanup')
    artifacts = pages(api, '/actions/artifacts', 'artifacts')
    retained = {}
    pattern = re.compile(rf'(repository|backend|docker|frontend)-{run_id}-\d+(?:-([1-4]))?')
    for artifact in artifacts:
        if artifact['workflow_run']['id'] != run_id or artifact['expired']:
            continue
        match = pattern.fullmatch(artifact['name'])
        if not match:
            continue
        role = match[1] + ('-' + match[2] if match[2] else '')
        if role not in ROLES:
            continue
        if role not in retained or artifact['id'] > retained[role]['id']:
            retained[role] = artifact
    if set(retained) != ROLES:
        raise ValueError('All seven current reports must be available before cleanup')
    keep_ids = {artifact['id'] for artifact in retained.values()}
    runs = {}
    targets = []
    for artifact in artifacts:
        owner = artifact['workflow_run']['id']
        if owner == run_id:
            if artifact['id'] not in keep_ids and pattern.fullmatch(artifact['name']):
                targets.append(artifact)
            continue
        if owner not in runs:
            runs[owner] = api(f'/actions/runs/{owner}')
        old = runs[owner]
        if (old['workflow_id'] == current['workflow_id']
                and old['status'] == 'completed'
                and old['created_at'] < current['created_at']):
            targets.append(artifact)
    return sorted(keep_ids), targets


def main():
    base = os.environ['GITHUB_API_URL'] + '/repos/' + os.environ['GITHUB_REPOSITORY']
    def api(path, method='GET'):
        request = urllib.request.Request(base + path, method=method, headers={
            'Authorization': 'Bearer ' + os.environ['GITHUB_TOKEN'],
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
        })
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read()
            return json.loads(body) if body else None
    keep, targets = plan(api, int(os.environ['GITHUB_RUN_ID']))
    print(json.dumps({'retained_artifact_ids': keep, 'delete_count': len(targets)}), flush=True)
    for artifact in targets:
        api('/actions/artifacts/' + str(artifact['id']), 'DELETE')
        print(json.dumps({'deleted_artifact_id': artifact['id'], 'name': artifact['name']}), flush=True)


if __name__ == '__main__':
    main()
