@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  User Management System - Start Script
REM  Start the Spring Boot service in background
REM ============================================================

REM ---------- Config ----------
set "APP_JAR=..\target\user-management-1.0.0.jar"
set "SERVER_PORT=8080"
set "LOG_DIR=..\logs"
set "LOG_FILE=%LOG_DIR%\app.log"
set "PID_FILE=%LOG_DIR%\app.pid"
set "STARTUP_TIMEOUT=60"
REM ----------------------------

echo ============================================
echo   User Management System - Start
echo ============================================

REM --- Ensure log directory ---
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM --- Resolve Java executable (JAVA_HOME first, then PATH) ---
set "JAVA_EXE="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    where java.exe >nul 2>&1
    if !errorlevel! equ 0 (
        set "JAVA_EXE=java.exe"
    ) else (
        echo [ERROR] Java not found. Please set JAVA_HOME or add Java to PATH.
        pause
        exit /b 1
    )
)
echo [INFO] JAVA_HOME: !JAVA_HOME!
echo [INFO] Java exe  : !JAVA_EXE!

REM --- Verify JAR exists ---
pushd "%~dp0"
if not exist "%APP_JAR%" (
    echo [ERROR] JAR not found: %APP_JAR%
    echo         Run "mvn clean package" in project root first.
    popd
    exit /b 1
)
for %%f in ("%APP_JAR%") do echo [INFO] JAR       : %%~ff

REM ============================================
REM  Pre-flight: check if already running
REM ============================================
call :find_existing
if defined EXISTING_PID (
    echo [ERROR] Service already running ^(PID: !EXISTING_PID!^).
    echo         Run stop.bat first.
    popd
    exit /b 1
)
echo [INFO] Port %SERVER_PORT% is free.

REM ============================================
REM  Start service in background
REM ============================================
echo [INFO] Starting service ...

start "" /min cmd /c ""!JAVA_EXE!" -jar "%APP_JAR%" --server.port=%SERVER_PORT% >> "%LOG_FILE%" 2>&1"

REM ============================================
REM  Wait for startup (health-check loop)
REM ============================================
set "NEW_PID="
set "ELAPSED=0"

:wait_loop
    REM Wait 2 seconds between checks (uses ping, works in cmd.exe and Git Bash)
    ping -n 3 127.0.0.1 >nul 2>&1
    set /a ELAPSED+=2

    REM Detect PID by matching JAR name in command line (not just port)
    call :detect_pid

    if defined NEW_PID (
        REM Double-check: the PID we found is actually listening on our port
        netstat -ano | findstr ":%SERVER_PORT%" | findstr "LISTENING" | findstr "!NEW_PID!" >nul 2>&1
        if !errorlevel! equ 0 (
            REM Write PID without trailing space — no space before >
            echo !NEW_PID!>"%PID_FILE%"
            echo.
            echo [OK] Service started successfully
            echo      PID : !NEW_PID!
            echo      Port: %SERVER_PORT%
            echo      Log : %LOG_FILE%
            goto :success
        )
    )

    if !ELAPSED! geq %STARTUP_TIMEOUT% (
        echo.
        echo [ERROR] Startup timed out after %STARTUP_TIMEOUT%s.
        echo         Check log for errors: %LOG_FILE%
        popd
        exit /b 1
    )

    echo [INFO] Waiting for service to be ready ... ^(!ELAPSED!s^)
    goto :wait_loop

:success
popd
echo.
echo   Login page : http://localhost:%SERVER_PORT%/login.html
echo   Manage page: http://localhost:%SERVER_PORT%/index.html
echo ============================================
exit /b 0


REM ========================================================================
REM  Subroutines
REM ========================================================================

REM ---- find_existing ----
REM Check if another instance of this service is already running.
REM Sets EXISTING_PID if found (verified by command-line match on JAR name).
:find_existing
    set "EXISTING_PID="

    REM (1) Check PID file — verify it still points to our JAR
    if exist "%PID_FILE%" (
        REM Use tokens=1 so trailing spaces in the file don't break the PID
        for /f "tokens=1" %%p in (%PID_FILE%) do set "CANDIDATE_PID=%%p"
        if defined CANDIDATE_PID (
            call :proc_is_ours "!CANDIDATE_PID!"
            if !errorlevel! equ 0 (
                set "EXISTING_PID=!CANDIDATE_PID!"
                exit /b 0
            )
            REM Stale PID file — clean up
            del "%PID_FILE%" >nul 2>&1
        )
    )

    REM (2) Fallback: check all java processes on our port (first match wins)
    for /f "tokens=5" %%a in (
        'netstat -ano ^| findstr ":%SERVER_PORT%" ^| findstr "LISTENING" 2^>nul'
    ) do (
        if not defined EXISTING_PID (
            call :proc_is_ours "%%a"
            if !errorlevel! equ 0 (
                set "EXISTING_PID=%%a"
                echo %%a>"%PID_FILE%"
            )
        )
    )
    exit /b 0

REM ---- detect_pid ----
REM Find the java process running our JAR.
REM Sets NEW_PID (empty if not found).
:detect_pid
    set "NEW_PID="

    REM Primary: wmic with command-line filter matching the JAR name
    for /f "tokens=2 delims==" %%a in (
        'wmic process where "name='java.exe' and commandline like '%%user-management%%'" get processid /value 2^>nul ^| findstr /i "ProcessId"'
    ) do (
        set "NEW_PID=%%a"
        exit /b 0
    )

    REM Fallback: PowerShell (for systems where wmic is removed)
    for /f %%a in (
        'powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -like '*user-management*' } | Select-Object -First 1 -ExpandProperty ProcessId" 2^>nul'
    ) do (
        set "NEW_PID=%%a"
    )
    exit /b 0

REM ---- proc_is_ours ----
REM %1 = PID
REM Returns errorlevel 0 if the process is a java executable running user-management JAR.
:proc_is_ours
    set "CHK_PID=%~1"
    if "%CHK_PID%"=="" exit /b 1

    REM Primary: wmic
    for /f "tokens=2 delims==" %%a in (
        'wmic process where "processid=%CHK_PID% and name like '%%java%%'" get commandline /value 2^>nul ^| findstr /i "user-management"'
    ) do (
        exit /b 0
    )

    REM Fallback: PowerShell
    for /f %%a in (
        'powershell -NoProfile -Command "$c=(Get-CimInstance Win32_Process -Filter \"ProcessId=%CHK_PID%\").CommandLine; if($c -like '*user-management*'){Write-Output 'YES'}" 2^>nul'
    ) do (
        if "%%a"=="YES" exit /b 0
    )
    exit /b 1
