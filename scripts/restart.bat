@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  User Management System - Restart Script
REM  Stop the service, wait for port release, then start
REM ============================================================

set "SERVER_PORT=8080"
set "PORT_WAIT_MAX=30"

echo ============================================
echo   User Management System - Restart
echo ============================================

REM ============================================
REM  Phase 1: Stop
REM ============================================
echo.
echo --- Phase 1: Stop ---
call "%~dp0stop.bat"

REM ============================================
REM  Phase 2: Wait for port to be released
REM ============================================
echo.
echo --- Phase 2: Wait for port release ---
set "WAITED=0"

:wait_port_free
    netstat -ano | findstr ":%SERVER_PORT%" | findstr "LISTENING" >nul 2>&1
    if !errorlevel! neq 0 (
        echo [OK]   Port %SERVER_PORT% is now free ^(after !WAITED!s^).
        goto :port_is_free
    )

    if !WAITED! geq %PORT_WAIT_MAX% (
        echo [ERROR] Port %SERVER_PORT% still in use after %PORT_WAIT_MAX%s.
        echo         Manually check: netstat -ano ^| findstr ":%SERVER_PORT%"
        exit /b 1
    )

    REM Wait 2 seconds using ping (works in cmd.exe and Git Bash)
    ping -n 3 127.0.0.1 >nul 2>&1
    set /a WAITED+=2
    echo [INFO] Waiting for port release ... ^(!WAITED!s^)
    goto :wait_port_free

:port_is_free

REM ============================================
REM  Phase 3: Start
REM ============================================
echo.
echo --- Phase 3: Start ---
call "%~dp0start.bat"
if %errorlevel% neq 0 (
    echo [ERROR] start.bat failed with exit code %errorlevel%.
    exit /b 1
)

echo.
echo [OK] Restart completed successfully.
echo ============================================

endlocal
exit /b 0
