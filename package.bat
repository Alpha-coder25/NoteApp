@echo off
REM =====================================================================
REM NoteApp - Windows packaging via jpackage
REM Prerequisites: JDK 17+ on PATH (or set JAVA_HOME), Maven build done:
REM     mvn clean package -DskipTests
REM
REM Produces target\dist\NoteApp\NoteApp.exe  (self-contained app image
REM bundling a private Java runtime - users do NOT need Java installed).
REM
REM To build a real Setup.exe installer instead, install WiX Toolset 3.x
REM and run with the --type exe option (see bottom of this file).
REM =====================================================================

if "%JAVA_HOME%"=="" (
    echo JAVA_HOME is not set. Point it to a JDK 17+ installation.
    exit /b 1
)

set APP_VERSION=1.0.0

echo Building fat jar...
call mvn -q clean package -DskipTests
if errorlevel 1 exit /b 1

echo Creating Windows application image...
"%JAVA_HOME%\bin\jpackage" ^
  --type app-image ^
  --name NoteApp ^
  --app-version %APP_VERSION% ^
  --vendor "NoteApp Project" ^
  --description "Offline note-taking desktop application" ^
  --input target ^
  --main-jar NoteApp.jar ^
  --dest target\dist

if errorlevel 1 (
    echo jpackage failed.
    exit /b 1
)

echo.
echo Done. Executable: target\dist\NoteApp\NoteApp.exe

REM ---------------------------------------------------------------------
REM Optional: real installer (requires WiX Toolset 3.x installed):
REM
REM "%JAVA_HOME%\bin\jpackage" --type exe --name NoteApp ^
REM   --app-version %APP_VERSION% --vendor "NoteApp Project" ^
REM   --description "Offline note-taking desktop application" ^
REM   --input target --main-jar NoteApp.jar --win-menu --win-shortcut ^
REM   --win-dir-chooser --dest target\dist
REM ---------------------------------------------------------------------
