@echo off
rem MiniForum 启动脚本（Windows）
rem 用法：
rem   scripts\start.bat          直接运行（前台）
rem   scripts\start.bat --build  先 mvn package 再运行
setlocal
cd /d "%~dp0\.."

set JAR=target\mini-forum-1.0.0.jar

if not exist "%JAR%" (
    echo [MiniForum] 未找到 %JAR%，先执行构建...
    call mvn package -DskipTests
    if errorlevel 1 exit /b 1
)

if "%1"=="--build" (
    echo [MiniForum] 重新构建...
    call mvn package -DskipTests
    if errorlevel 1 exit /b 1
)

echo [MiniForum] 启动中... 访问 http://localhost:8090
java -jar "%JAR%"
endlocal
