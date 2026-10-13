@echo off
setlocal
cd /d "%~dp0"
py -3 --version >nul 2>&1
if errorlevel 1 (
  echo Install Python 3.11 or newer with the Windows py launcher.
  pause
  exit /b 1
)
if not exist .venv\Scripts\python.exe (
  py -3 -m venv .venv
  if errorlevel 1 exit /b 1
)
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 exit /b 1
.venv\Scripts\python.exe generator\stage3_main.py
if errorlevel 1 (
  pause
  exit /b 1
)
.venv\Scripts\python.exe -m unittest discover -s tests -v
if errorlevel 1 (
  pause
  exit /b 1
)
echo Stage 3 schematics and previews are in output\stage3.
pause
