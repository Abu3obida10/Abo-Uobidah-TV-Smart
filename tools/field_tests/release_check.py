#!/usr/bin/env python3
"""Release gate: APK + update.json must agree on version, URL, SHA-256 and signer.
Usage: release_check.py app-release.apk update.json [--repo OWNER/REPO]
Needs apksigner (Android build-tools) on PATH. Exit 1 on any mismatch."""
import hashlib, json, re, subprocess, sys, pathlib
root = pathlib.Path(__file__).resolve().parents[2]
apk, man = pathlib.Path(sys.argv[1]), json.loads(pathlib.Path(sys.argv[2]).read_text('utf-8'))
repo = sys.argv[sys.argv.index('--repo')+1] if '--repo' in sys.argv else 'Abu3obida10/Abo-Uobidah-TV-Smart'
props = dict(l.split('=',1) for l in (root/'gradle.properties').read_text().splitlines() if l.startswith('appVersion'))
pin = (root/'release/EXPECTED_SIGNER_SHA256.txt').read_text().strip().lower()
bad = []
def chk(name, ok, detail=''):
    print(('PASS ' if ok else 'FAIL ')+name+(' '+detail if detail and not ok else '')); 
    if not ok: bad.append(name)
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
chk('manifest sha256 == APK sha256', man.get('sha256','').lower()==sha, f"{man.get('sha256')} vs {sha}")
chk('manifest versionName == gradle.properties', man.get('versionName')==props['appVersionName'])
chk('manifest versionCode == gradle.properties', int(man.get('versionCode',0))==int(props['appVersionCode']))
chk('tag == v+versionName', man.get('tag')=='v'+man.get('versionName',''))
chk('apkUrl is this release\'s asset', man.get('apkUrl','').startswith(f"https://github.com/{repo}/releases/download/{man.get('tag')}/") and man['apkUrl'].endswith(apk.name))
chk('packageName', man.get('packageName')=='com.uobidah.launcher')
out = subprocess.run(['apksigner','verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True)
chk('apksigner verify', out.returncode==0, out.stderr[:200])
m = re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})', out.stdout)
chk('signer == pinned owner certificate', bool(m) and all(x==pin for x in m), str(m))
bd = subprocess.run(['aapt2','dump','badging',str(apk)],capture_output=True,text=True)
chk('APK not debuggable (aapt2 available)', bd.returncode==0 and 'application-debuggable' not in bd.stdout, bd.stderr[:200])
sys.exit(1 if bad else 0)
