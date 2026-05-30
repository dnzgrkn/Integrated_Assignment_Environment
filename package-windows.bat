@echo off

:: Config
set APP_NAME=IAE
set MAIN_JAR=iae-1.0.0-SNAPSHOT.jar
set LAUNCHER_CLASS=com.iae.Launcher
set VERSION=1.0.0
set VENDOR=Team8
set INPUT_DIR=target
set OUTPUT_DIR=installer-output


echo Building Maven project...
call mvn clean package -DskipTests -q

echo Running jpackage...
jpackage ^
  --input %INPUT_DIR% ^
  --name %APP_NAME% ^
  --main-jar %MAIN_JAR% ^
  --main-class %LAUNCHER_CLASS% ^
  --type msi ^
  --app-version %VERSION% ^
  --vendor "%VENDOR%" ^
  --win-shortcut ^
  --win-menu ^
  --win-dir-chooser ^
  --dest %OUTPUT_DIR%

echo Done! Installer is in %OUTPUT_DIR%\
for %%F in (%OUTPUT_DIR%\*.msi) do echo Full path: %%~fF
pause