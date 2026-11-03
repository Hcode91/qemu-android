#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Record legacy guest findings; this research job produces no release image."""
import json
import os
import pathlib
import sys
import urllib.request
if os.environ.get('GITHUB_ACTIONS') != 'true': sys.exit('Security checks run in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
report = root / 'android/reports/aosp-userspace'
projects = json.loads((report / 'checkout.json').read_text())['checked_out']
legacy = json.loads((root / 'android/aosp/sources.json').read_text())
coordinates = [{'name': p['name'], 'commit': p['commit']} for p in projects]
coordinates += [{'name': key, 'commit': legacy[key]['commit']} for key in ['kernel', 'compiler']]
request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
    json.dumps({'queries': [{'commit': p['commit']} for p in coordinates]}).encode(),
    {'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=90) as response: results = json.load(response)['results']
if len(results) != len(coordinates): raise ValueError('Incomplete vulnerability response')
findings = [{'dependency': p, 'osv': r} for p, r in zip(coordinates, results)]
(report / 'guest-osv.json').write_text(json.dumps({'scope': 'Legacy guest source commits, incomplete database coverage; not a release clearance', 'dependencies': findings}, indent=2) + '\n')
count = sum(len(r.get('vulns', [])) for r in results)
print(f'Legacy guest scan returned {count} finding references; see guest-osv.json. No release image is produced.')
if count: print('::warning::Known legacy guest findings retained for research review; this job does not produce a release or phone guest bundle.')
