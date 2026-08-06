@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  User Management System - Stop Script
REM  Stop the Spring Boot service safely
REM ============================================================

set "SERVER_PORT=8080"
set "LOG_DIR=..\logs"
set "PID_FILE=%LOG_DIR%\app.pid"
set "TMPFILE=%TEMP%\nanobot_stop_%RANDOM%.txt"

echo ============================================
echo   User Management System - Stop
echo ============================================

set "KILLED=0"

REM ============================================
REM  Method 1: Stop by PID file (verified)
REM ============================================
if exist "%PID_FILE%" (
    REM tokens=1 handles trailing spaces in file gracefully
    for /f "tokens=1" %%p in (%PID_FILE%) do set "PID=%%p"
    if defined PID (
        call :proc_is_ours "!PID!"
        if !errorlevel! equ 0 (
            echo [INFO] Stopping verified process ^(PID: !PID!^) ...
            call :do_kill "!PID!"
            if !errorlevel! equ 0 (set "KILLED=1")
        ) else (
            echo [INFO] PID file exists ^(!PID!^) but it is NOT our service ^(stale^).
        )
    )
    del "%PID_FILE%" >nul 2>&1
)

REM ============================================
REM  Method 2: Stop by port (verified fallback)
REM ============================================
echo [INFO] Checking port %SERVER_PORT% for leftover processes ...

netstat -ano | findstr ":%SERVER_PORT%" | findstr "LISTENING" > "%TMPFILE%" 2>nul

REM First verified match wins - kill it then stop searching
REM (there can only be one instance of our service)
for /f "usebackq tokens=5" %%a in ("%TMPFILE%") do (
    if not defined KILLED_DONE (
        call :proc_is_ours "%%a"
        if !errorlevel! equ 0 (
            echo [INFO] Found our service on port %SERVER_PORT% ^(PID: %%a^) ...
            call :do_kill "%%a"
            if !errorlevel! equ 0 (
                set "KILLED=1"
                set "KILLED_DONE=1"
            )
        ) else (
            echo [WARN] Port %SERVER_PORT% is used by PID %%a, but it is NOT our service.
        )
    )
)

del "%TMPFILE%" >nul 2>&1

REM ============================================
REM  Summary
REM ============================================
if "%KILLED%"=="0" (
    if not defined KILLED_DONE (
        echo [INFO] No running service found ^(port %SERVER_PORT% is free^).
    ) else (
        echo [OK]   Service stopped.
    )
) else (
    echo [OK]   Service stopped.
)
echo ============================================

endlocal
exit /b 0


REM ========================================================================
REM  Subroutines
REM ========================================================================

REM ---- do_kill ----
REM %1 = PID. Tries graceful kill first, then force kill.
REM Returns errorlevel 0 on success, 1 on failure.
:do_kill
    taskkill /PID "%~1" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK]   Stopped process PID: %~1
        exit /b 0
    )
    REM Graceful kill failed, force kill
    taskkill /PID "%~1" /F >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK]   Force-stopped process PID: %~1
        exit /b 0
    )
    echo [ERROR] Failed to stop process PID: %~1
    exit /b 1

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
