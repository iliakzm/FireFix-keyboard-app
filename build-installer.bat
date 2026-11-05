@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Building jar with Maven...
call mvn -q clean package
if errorlevel 1 goto :fail
if not exist target\firefix.jar (
  echo target\firefix.jar not found.
  goto :fail
)

echo [2/4] Preparing input folder...
if exist build-input rmdir /s /q build-input
mkdir build-input
copy /y target\firefix.jar build-input\ >nul

set ICON_ARG=
if exist logo.ico (
  set ICON_ARG=--icon logo.ico
) else (
  echo (no logo.ico found - installer will use the default Java icon)
)

echo [3/4] Creating installer with jpackage...
if exist installer rmdir /s /q installer
jpackage ^
  --type exe ^
  --name FireFix ^
  --app-version 1.0.0 ^
  --vendor "FireFix" ^
  --description "FireFix" ^
  --input build-input ^
  --main-jar firefix.jar ^
  --main-class app.Main ^
  --dest installer ^
  --add-modules java.desktop,java.logging,java.prefs,java.datatransfer,jdk.unsupported ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --win-per-user-install ^
  --win-upgrade-uuid 7c3f2a1e-5b4d-4e8a-9a61-2f0d8b6c1e55 ^
  --java-options "-Xms16m -Xmx192m -XX:+UseSerialGC -XX:TieredStopAtLevel=1" ^
  %ICON_ARG%
if errorlevel 1 goto :fail

echo [4/4] Renaming installer...
ren "installer\FireFix-1.0.0.exe" "FireFix-Setup.exe"
if errorlevel 1 goto :fail

echo Done. Installer: installer\FireFix-Setup.exe
goto :end

:fail
echo.
echo Build failed. Check the messages above.
exit /b 1

:end
endlocal
