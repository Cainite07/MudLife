"""Publish the same signed MudLife release to GitHub and Gitee. No plaintext credentials."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
CERT = '00da3d63a98a2f73565b306c18c67ca2f4fe27fc2ae71599544f96593637c2df'
ENV = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='Never')


def git(*args):
    result = subprocess.run(['git', *args], cwd=ROOT, env=ENV, capture_output=True,
                            text=True, encoding='utf-8', timeout=120)
    if result.returncode:
        raise ValueError('Git operation failed: ' + ' '.join(args[:2]))
    return result.stdout.strip()


def credential(host):
    result = subprocess.run(['git', '-c', 'credential.interactive=never', 'credential', 'fill'],
                            input=f'protocol=https\nhost={host}\n\n', cwd=ROOT, env=ENV,
                            capture_output=True, text=True, encoding='utf-8', timeout=20)
    fields = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    if result.returncode or not fields.get('password'):
        raise ValueError(f'Missing credential for {host}; configure Git Credential Manager first.')
    return fields['password']


class Api:
    def __init__(self, platform, token):
        self.platform, self.token = platform, token
        self.base = ('https://api.github.com/repos/Cainite07/MudLife' if platform == 'github'
                     else 'https://gitee.com/api/v5/repos/cainite/MudLife')

    def request(self, path, method='GET', values=None, payload=None, content_type=None):
        url = path if path.startswith('https://') else self.base + path
        headers = {'User-Agent': 'MudLife-release', 'Accept': 'application/json'}
        if self.platform == 'github':
            headers['Authorization'] = 'Bearer ' + self.token
            if values is not None:
                payload, content_type = json.dumps(values).encode(), 'application/json'
        elif values is not None:
            payload = urllib.parse.urlencode(dict(values, access_token=self.token)).encode()
            content_type = 'application/x-www-form-urlencoded'
        else:
            url += ('&' if '?' in url else '?') + urllib.parse.urlencode({'access_token': self.token})
        if content_type:
            headers['Content-Type'] = content_type
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=payload, headers=headers,
                                                               method=method), timeout=180) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            # Error URLs and bodies may contain credentials; report only platform/status.
            raise ValueError(f'{self.platform} API HTTP {error.code} ({method})') from None
        except Exception:
            raise ValueError(f'{self.platform} API connection failed ({method}); safe to resume.') from None

    def release(self, tag, head, body):
        releases = self.request('/releases?per_page=100')
        existing = next((r for r in releases if r['tag_name'] == tag), None)
        if existing:
            return existing
        values = dict(tag_name=tag, name=f'MudLife {tag}', body=body, target_commitish=head)
        values.update(draft=True, prerelease=False) if self.platform == 'github' else values.update(prerelease='true')
        return self.request('/releases', 'POST', values)

    def upload(self, release, path):
        suffix = f"/releases/{release['id']}/" + ('assets' if self.platform == 'github' else 'attach_files')
        assets = self.request(suffix + '?per_page=100')
        existing = next((a for a in assets if a.get('name') == path.name), None)
        if existing:
            if self.platform == 'github' and existing.get('size') != path.stat().st_size:
                raise ValueError('Existing release asset differs: ' + path.name)
            print(f'{self.platform}: keeping existing {path.name}', flush=True)
            return
        data = path.read_bytes()
        if self.platform == 'github':
            url = release['upload_url'].split('{')[0] + '?' + urllib.parse.urlencode({'name': path.name})
            self.request(url, 'POST', payload=data, content_type='application/octet-stream')
        else:
            boundary = 'mudlife-' + uuid.uuid4().hex
            start = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                     f'filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()
            self.request(suffix, 'POST', payload=start + data + f'\r\n--{boundary}--\r\n'.encode(),
                         content_type='multipart/form-data; boundary=' + boundary)
        print(f'{self.platform}: uploaded {path.name}', flush=True)

    def publish(self, release, tag, body):
        values = dict(tag_name=tag, name=f'MudLife {tag}', body=body, prerelease=False)
        if self.platform == 'github':
            values.update(draft=False, make_latest='true')
        else:
            values['prerelease'] = 'false'
        return self.request(f"/releases/{release['id']}", 'PATCH', values)


def verify_download(url, expected_path, direct=False):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if direct else urllib.request.build_opener()
    digest, size = hashlib.sha256(), 0
    try:
        with opener.open(urllib.request.Request(url, headers={'User-Agent': 'MudLife-release'}), timeout=90) as response:
            while chunk := response.read(1024 * 1024):
                digest.update(chunk)
                size += len(chunk)
    except Exception:
        raise ValueError('Public download verification failed: ' + urllib.parse.urlsplit(url).hostname) from None
    if size != expected_path.stat().st_size or digest.hexdigest() != hashlib.sha256(expected_path.read_bytes()).hexdigest():
        raise ValueError('Release asset content mismatch: ' + expected_path.name)
    print(f'Verified {"DIRECT " if direct else ""}{urllib.parse.urlsplit(url).hostname}: '
          f'{expected_path.name}, {size} bytes, SHA256 {digest.hexdigest()}', flush=True)


def main():
    os.chdir(ROOT)
    if git('status', '--porcelain'):
        raise ValueError('Commit the intended source changes before publishing.')
    manifest = json.loads((ROOT / 'version.json').read_text(encoding='utf-8'))
    version, code = manifest['versionName'], manifest['versionCode']
    gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    if re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1] != version or int(re.search(r'versionCode\s*=\s*(\d+)', gradle)[1]) != code:
        raise ValueError('Gradle and version.json disagree.')
    tag, head = 'v' + version, git('rev-parse', 'HEAD')
    name = f'MudLife-{version}.apk'
    gitee_url = f'https://gitee.com/cainite/MudLife/releases/download/{tag}/{name}'
    github_url = f'https://github.com/Cainite07/MudLife/releases/download/{tag}/{name}'
    if manifest['downloadUrl'] != github_url or manifest['mirrors'][0] != gitee_url:
        raise ValueError('Manifest must point to this release, with Gitee first.')
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    sdk = Path(os.environ.get('ANDROID_HOME', r'C:\Android\Sdk')) / 'build-tools/35.0.0'
    signer = sdk / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    result = subprocess.run([str(signer), 'verify', '--print-certs', str(apk)],
                            capture_output=True, text=True, encoding='utf-8', env=ENV)
    if result.returncode or CERT not in result.stdout.lower():
        raise ValueError('APK signature does not match the permanent release certificate.')
    aapt = sdk / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    metadata = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], capture_output=True, text=True, encoding='utf-8')
    first = metadata.stdout.splitlines()[0] if metadata.returncode == 0 else ''
    if not all(value in first for value in ["name='cn.mudlife.app'", f"versionCode='{code}'", f"versionName='{version}'"]):
        raise ValueError('APK package/version mismatch.')
    apis = [Api('github', credential('github.com')), Api('gitee', credential('gitee.com'))]
    # Read-only preflight: refuse unrelated branch histories and conflicting tags.
    for remote in ['origin', 'gitee']:
        git('fetch', remote, 'main')
        git('merge-base', '--is-ancestor', 'FETCH_HEAD', head)
        remote_tag = git('ls-remote', remote, 'refs/tags/' + tag)
        if remote_tag and remote_tag.split()[0] != head:
            raise ValueError(f'{remote}: existing tag points to a different commit.')
    tags = git('tag', '--list', tag)
    if tags and git('rev-list', '-n', '1', tag) != head:
        raise ValueError('Local version tag belongs to another commit.')
    if not tags:
        git('tag', tag, head)
    output = ROOT / 'app/build/outputs/dual-release'
    output.mkdir(exist_ok=True)
    target = output / name
    shutil.copyfile(apk, target)
    checksum = hashlib.sha256(target.read_bytes()).hexdigest()
    sums = output / 'SHA256SUMS.txt'
    sums.write_text(f'{checksum}  {name}\n', encoding='utf-8')
    body = (f"{manifest['changelog']}\n\nAndroid 8.0+ · 正式包 cn.mudlife.app · {version} / {code}\n\n"
            f"沿用固定发行签名，支持同签名系列覆盖升级。宣传图均为虚构演示数据。\n\n"
            f"源码提交：`{head}`\n\nSHA-256：`{checksum}`\n\n"
            f"[Gitee 国内下载]({gitee_url}) · [GitHub 下载]({github_url})")
    for remote in ['origin', 'gitee']:
        git('push', remote, 'refs/tags/' + tag)
    releases = [(api, api.release(tag, head, body)) for api in apis]
    for api, release in releases:
        for path in [target, sums]:
            api.upload(release, path)
    # Gitee preview assets are public. Verify without a proxy before finalizing either platform.
    for path in [target, sums]:
        verify_download(gitee_url.rsplit('/', 1)[0] + '/' + path.name, path, direct=True)
    for api, release in releases:
        api.publish(release, tag, body)
    for path in [target, sums]:
        verify_download(github_url.rsplit('/', 1)[0] + '/' + path.name, path)
    for remote in ['origin', 'gitee']:
        git('push', remote, head + ':refs/heads/main')
        if git('ls-remote', remote, 'refs/heads/main').split()[0] != head:
            raise ValueError(remote + ': main verification failed.')
    for api, release in releases:
        final = api.request(f"/releases/{release['id']}")
        if final.get('draft') or final.get('prerelease') or final['tag_name'] != tag:
            raise ValueError(api.platform + ': release is not final.')
    print(f'Published {tag}, commit {head}\nhttps://github.com/Cainite07/MudLife/releases/tag/{tag}\n'
          f'https://gitee.com/cainite/MudLife/releases/tag/{tag}', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, IndexError) as error:
        # Never include HTTP request objects, credential helper output, or server response bodies.
        print(str(error) if isinstance(error, ValueError) else 'Local release preparation failed; check inputs.', file=sys.stderr)
        sys.exit(1)
