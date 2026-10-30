"""Maintainer GitHub Release helper. Authentication stays in memory via Git Credential Manager."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT = Path(__file__).resolve().parent.parent
REPO = 'rainthink-vanilla/HAUTPocket'
parser = argparse.ArgumentParser()
parser.add_argument('operation',choices=['inspect','prepare','publish','verify'])
parser.add_argument('--tag',default='v1.0.0')
parser.add_argument('--commit')
parser.add_argument('--notes',type=Path)
parser.add_argument('--asset',type=Path,action='append',default=[])
args = parser.parse_args()
env = dict(os.environ,GCM_INTERACTIVE='Never',GIT_TERMINAL_PROMPT='0')
credential = subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,cwd=ROOT)
values = dict(line.split('=',1) for line in credential.stdout.splitlines() if '=' in line)
token = values.get('password')
if credential.returncode or not token: sys.exit('No usable GitHub credential in Git Credential Manager; no token was printed or saved.')
headers={'Authorization':f'Bearer {token}','Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'HAUTPocket-release'}

def request(path,method='GET',body=None,content_type='application/json',absolute=False):
    url = path if absolute else f'https://api.github.com/repos/{REPO}{path}'
    data = json.dumps(body).encode() if isinstance(body,dict) else body
    req=Request(url,data=data,headers=dict(headers,**({'Content-Type':content_type} if data is not None else {})),method=method)
    try:
        with urlopen(req,timeout=120) as response: return json.loads(response.read())
    except HTTPError as error:
        if error.code == 404: return None
        raise RuntimeError(f'GitHub returned HTTP {error.code} for {method}; response content suppressed') from None

def release():
    # Drafts may not be returned by /tags; authenticated list includes them.
    return next((r for r in request('/releases?per_page=100') if r['tag_name']==args.tag),None)

def snapshot(r):
    return {'id':r['id'],'tag':r['tag_name'],'draft':r['draft'],'prerelease':r['prerelease'],'url':r['html_url'],
        'assets':[{'name':a['name'],'size':a['size'],'url':a['browser_download_url']} for a in r['assets']]}

if args.operation == 'inspect':
    repository=request('')
    print(json.dumps({'repo':REPO,'default_branch':repository['default_branch'],'releases':[snapshot(r) for r in request('/releases?per_page=20')]},ensure_ascii=False))
elif args.operation == 'prepare':
    if not args.commit or not args.notes or not args.asset: sys.exit('prepare requires exact commit, notes, and assets')
    existing=release()
    if existing and not existing['draft']: sys.exit('A published release already exists; refusing to replace it')
    r=existing or request('/releases','POST',{'tag_name':args.tag,'target_commitish':args.commit,'name':f'口袋工大 {args.tag} 正式版',
        'body':args.notes.read_text(encoding='utf-8'),'draft':True,'prerelease':False})
    for asset in args.asset:
        if any(a['name']==asset.name for a in r['assets']): sys.exit('Draft already contains an asset with this name; inspect before replacing')
        upload=r['upload_url'].split('{',1)[0]+'?name='+asset.name
        request(upload,'POST',asset.read_bytes(),'application/octet-stream',absolute=True)
    print(json.dumps(snapshot(request(f"/releases/{r['id']}")),ensure_ascii=False))
elif args.operation == 'publish':
    r=release()
    if not r or not r['draft']: sys.exit('No matching draft release to publish')
    if len(r['assets']) != 3: sys.exit('Expected exactly APK, source ZIP and checksum assets')
    r=request(f"/releases/{r['id']}",'PATCH',{'draft':False,'prerelease':False,'make_latest':'true'})
    print(json.dumps(snapshot(r),ensure_ascii=False))
else:
    r=release()
    if not r or r['draft'] or r['prerelease']: sys.exit('Release is not a public stable release')
    for asset in args.asset:
        remote=next((a for a in r['assets'] if a['name']==asset.name),None)
        if not remote: sys.exit(f'Missing asset: {asset.name}')
        # Public downloads: never send the API token across redirect hosts.
        req=Request(remote['browser_download_url'],headers={'User-Agent':'HAUTPocket-release-verify'})
        with urlopen(req,timeout=120) as response: digest=hashlib.sha256(response.read()).hexdigest()
        if digest != hashlib.sha256(asset.read_bytes()).hexdigest(): sys.exit(f'Checksum mismatch: {asset.name}')
        print(f'Published asset SHA-256 verified: {asset.name} {digest}')
    print(json.dumps(snapshot(r),ensure_ascii=False))
