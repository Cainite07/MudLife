@echo off
chcp 65001 >nul
echo ========================================================
echo         MudLife 一键极速编译与真机热更新
echo ========================================================

echo [1/3] 本地增量编译 Release APK...
call gradlew.bat assembleRelease
if errorlevel 1 (
    echo [错误] 编译失败，请检查报错日志。
    pause
    exit /b 1
)

echo [2/3] 自动探测并连接设备...
python "C:\工具\platform-tools\adb_auto_connect.py"

set DEV=192.168.1.106:5555
if exist "C:\工具\platform-tools\last_endpoint.txt" (
    set /p DEV=<"C:\工具\platform-tools\last_endpoint.txt"
)

echo [3/3] 推送并覆盖安装至手机 (%DEV%)...
"C:\工具\platform-tools\adb.exe" connect %DEV% >nul 2>&1
"C:\工具\platform-tools\adb.exe" -s %DEV% install -r "app\build\outputs\apk\release\app-release.apk"

if errorlevel 1 (
    echo [提示] 正在重新建连并重试...
    "C:\工具\platform-tools\adb.exe" connect 192.168.1.106:5555
    "C:\工具\platform-tools\adb.exe" -s 192.168.1.106:5555 install -r "app\build\outputs\apk\release\app-release.apk"
)

echo.
echo [成功] MudLife APK 已无缝覆盖安装到手机！
pause
