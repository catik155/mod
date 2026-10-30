@echo off
cd /d "%~dp0"
echo Building Minute Events 2.0.0...
call gradlew.bat build
if errorlevel 1 (
  echo Build failed. Check the error above.
  pause
  exit /b 1
)
echo.
echo Build complete. JAR files are in build\libs\
pause
