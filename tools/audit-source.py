"""Audit only files eligible for publication; never print matched secret values."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
result = subprocess.run(['git','-c',f'safe.directory={root.as_posix()}','ls-files','-co','--exclude-standard','-z'],cwd=root,capture_output=True,check=True)
paths = sorted(set(result.stdout.decode('utf-8').split('\0')) - {''})
patterns = {
    'GitHub credential': re.compile(r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})'),
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'AWS access key': re.compile(r'\bAKIA[A-Z0-9]{16}\b'),
    'JWT': re.compile(r'\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'),
}
binary_allow = {'gradle/wrapper/gradle-wrapper.jar','signing/lineage.bin'}
issues = []
for name in paths:
    if name.startswith(('.toolchain/','.private-signing/','memory/','deliverables/')) or name.endswith(('.keystore','.jks','.p12','.apk','.idsig')) or name == 'local.properties':
        issues.append((name,'private/generated file eligible for publication')); continue
    path = root / name
    if not path.is_file(): issues.append((name,'missing candidate')); continue
    if name in binary_allow: continue
    try: content = path.read_text(encoding='utf-8-sig')
    except UnicodeDecodeError: issues.append((name,'unexpected binary')); continue
    for rule, pattern in patterns.items():
        match = pattern.search(content)
        if match: issues.append((name,f'{rule} at line {content.count(chr(10),0,match.start())+1}'))
if issues:
    for name, problem in issues: print(f'FAIL {name}: {problem}')
    sys.exit(1)
print(f'Publication source audit passed: {len(paths)} files, private paths excluded, no matched credential/key patterns.')
