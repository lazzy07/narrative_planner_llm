@echo off
setlocal enabledelayedexpansion

REM File name: run-planner.bat
REM Project:
REM Author: Lasantha M Senanayake
REM Date created: 2026-01-25 23:10:53
REM Date modified: 2026-01-27 01:09:53
REM ------

REM Resolve repo root relative to this script
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%

set PLANNER_JAR=%ROOT_DIR%planner\target\planner-1.0-SNAPSHOT.jar

REM Build if missing
if not exist "%PLANNER_JAR%" (
    echo ℹ️  Missing %PLANNER_JAR%
    echo    Building (shade/fat jar)...
    pushd "%ROOT_DIR%"
    call mvnw.cmd -q -DskipTests -pl planner -am package
    popd
)

REM Sanity check
if not exist "%PLANNER_JAR%" (
    echo ❌ Still missing %PLANNER_JAR%
    echo    Try: cd %ROOT_DIR% ^&^& mvnw.cmd -DskipTests -pl planner -am package
    exit /b 1
)

REM Run fat jar
java -jar "%PLANNER_JAR%" %*

