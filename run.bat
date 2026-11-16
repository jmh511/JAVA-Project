@echo off
rem Compile and run Desktop Planner. Data is saved to data\planner.txt in this folder.
cd /d "%~dp0"
if not exist out mkdir out

rem 1) Main program (Java 17 or newer).
javac -encoding UTF-8 -d out src\planner\*.java
if errorlevel 1 (
    echo Compile failed.
    pause
    exit /b 1
)

rem 2) Desktop mode (Windows native calls, needs JDK 22 or newer). If this fails,
rem    the program still runs as a normal window.
javac -encoding UTF-8 -cp out -d out src\planner\win\*.java >nul 2>&1
if errorlevel 1 echo Desktop mode skipped: needs JDK 22 or newer. Running as a normal window.

rem --enable-native-access lets the desktop mode call Windows functions without a warning.
java --enable-native-access=ALL-UNNAMED -version >nul 2>&1
if errorlevel 1 (
    start "" javaw -cp out planner.Main
) else (
    start "" javaw --enable-native-access=ALL-UNNAMED -cp out planner.Main
)
