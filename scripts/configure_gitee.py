"""One-time, interactive Gitee setup. Credentials go only to Git's credential helper."""
import getpass
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


def main():
    print("MudLife Gitee 发布凭据配置")
    print("请使用 Gitee 的私人令牌，勾选 projects（仓库）和 user_info（账号信息）权限。")
    print("令牌输入不会显示；不写入项目文件，也不要发送到聊天中。")
    token = getpass.getpass("请输入 Gitee 私人令牌：").strip()
    if not token:
        raise ValueError("未输入令牌，已取消。")
    url = "https://gitee.com/api/v5/user?" + urllib.parse.urlencode({"access_token": token})
    try:
        with urllib.request.urlopen(url, timeout=20) as response:
            profile = json.load(response)
    except urllib.error.HTTPError as error:
        raise ValueError(f"Gitee 验证未通过（HTTP {error.code}），请检查令牌和权限。") from None
    except Exception:
        raise ValueError("暂时无法连接 Gitee，未保存凭据。") from None
    login = profile.get("login", "")
    if login != "cainite":
        raise ValueError(f"当前令牌属于 {login or '未知账号'}，目标仓库属于 cainite，未保存。")
    helper = subprocess.run(["git", "config", "--get-all", "credential.helper"], capture_output=True, text=True)
    if "manager" not in helper.stdout.lower():
        raise ValueError("没有检测到 Windows Git 凭据管理器，未保存令牌。")
    env = os.environ.copy()
    env.update(GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="Never")
    result = subprocess.run(["git", "-c", "credential.interactive=never", "credential", "approve"],
        input=f"protocol=https\nhost=gitee.com\nusername={login}\npassword={token}\n\n",
        capture_output=True, text=True, encoding="utf-8", env=env)
    if result.returncode:
        raise ValueError("Windows 凭据保存失败，未写入其他位置。")
    print(f"已为 {login} 配置 Gitee 发布凭据，保存于 Windows 凭据管理器。")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyboardInterrupt) as error:
        print(str(error) if isinstance(error, ValueError) else "已取消。")
        sys.exit(1)
