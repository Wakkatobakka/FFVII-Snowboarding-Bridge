@echo off
setlocal
cd /d "%~dp0"
echo.
echo FFVII Snowboarding Bridge - Payload Builder v0.1.0
echo =====================================================
echo This tool does NOT download or include the game.
echo.
if "%~1"=="" (
  set /p GAMEJAR=Path to game-offline-english.jar: 
) else (
  set "GAMEJAR=%~1"
)
if "%~2"=="" (
  set /p GAMESP=Path to game-offline-english.sp: 
) else (
  set "GAMESP=%~2"
)
python build_payload.py "%GAMEJAR%" "%GAMESP%"
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Read the message above.
  pause
  exit /b 1
)
echo.
echo Done. Import FFVII_Snowboarding_Data_for_Bridge_v0.1.2.zip in the Android app.
pause
