@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0" || exit /b 1
echo 江大生活 · Jiangda Life 真机覆盖安装

call gradlew.bat --no-daemon -Pkotlin.compiler.execution.strategy=in-process packageReleaseApk
if errorlevel 1 exit /b 1

set "APK="
for %%F in ("app\build\outputs\distribution\JiangdaLife-*.apk") do if exist "%%~fF" set "APK=%%~fF"
if not defined APK exit /b 1

set "DEV="
for /f "delims=" %%D in ('python "C:\工具\adb\adb_auto_connect.py" endpoint %*') do set "DEV=%%D"
if not defined DEV exit /b 1
"C:\工具\adb\adb.exe" -s "%DEV%" install -r "%APK%"
exit /b %errorlevel%
