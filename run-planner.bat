@echo off
setlocal EnableDelayedExpansion

REM ---------------- Config ----------------

set APP_MAIN=nil.lazzy07.planner.App

set PLANNER_JAR=planner\target\planner-1.0-SNAPSHOT.jar
set DOMAIN_JAR=domain\target\domain-1.0-SNAPSHOT.jar
set LLM_JAR=llm\target\llm-1.0-SNAPSHOT.jar

REM Optional JVM options via environment variable
REM Example:
REM   set JAVA_OPTS=-Xmx8G -ea
set JAVA_OPTS=%JAVA_OPTS%

REM ---------------- Sanity checks ----------------

if not exist "%PLANNER_JAR%" (
  echo ❌ Missing %PLANNER_JAR%
  echo    Run: mvnw.cmd -DskipTests package
  exit /b 1
)

if not exist "%DOMAIN_JAR%" (
  echo ❌ Missing %DOMAIN_JAR%
  echo    Run: mvnw.cmd -DskipTests package
  exit /b 1
)

if not exist "%LLM_JAR%" (
  echo ❌ Missing %LLM_JAR%
  echo    Run: mvnw.cmd -DskipTests package
  exit /b 1
)

REM ---------------- Classpath ----------------

set CLASSPATH=%PLANNER_JAR%;%DOMAIN_JAR%;%LLM_JAR%

REM ---------------- Run ----------------

echo ▶ Running Planner...
java %JAVA_OPTS% -cp "%CLASSPATH%" %APP_MAIN% %*

