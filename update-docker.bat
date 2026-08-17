@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title dynamic-bot one-click update

set "UPDATE_REPO=https://github.com/half-drop/dynamic-bot.git"
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
set "REPO_DIR="

rem Accept either a real Git checkout or an existing source/deployment directory without .git.
if exist "%SCRIPT_DIR%\build.gradle.kts" if exist "%SCRIPT_DIR%\Dockerfile" set "REPO_DIR=%SCRIPT_DIR%"
if not defined REPO_DIR if exist "%SCRIPT_DIR%\dynamic-bot\build.gradle.kts" if exist "%SCRIPT_DIR%\dynamic-bot\Dockerfile" set "REPO_DIR=%SCRIPT_DIR%\dynamic-bot"
if not defined REPO_DIR if exist "%SCRIPT_DIR%\.git" set "REPO_DIR=%SCRIPT_DIR%"
if not defined REPO_DIR if exist "%SCRIPT_DIR%\dynamic-bot\.git" set "REPO_DIR=%SCRIPT_DIR%\dynamic-bot"

if not defined REPO_DIR (
    echo [ERROR] dynamic-bot directory was not found.
    echo Expected either:
    echo   %SCRIPT_DIR%build.gradle.kts
    echo or:
    echo   %SCRIPT_DIR%dynamic-bot\build.gradle.kts
    goto :fail
)

where git >nul 2>nul || (
    echo [ERROR] Git was not found in PATH.
    goto :fail
)
where docker >nul 2>nul || (
    echo [ERROR] Docker was not found in PATH.
    goto :fail
)
docker info >nul 2>nul || (
    echo [ERROR] Docker Desktop / Docker Engine is not running.
    goto :fail
)

echo [1/4] Syncing source from half-drop/dynamic-bot main...
if not exist "%REPO_DIR%\.git" (
    echo       Existing source directory is not a Git repository; initializing it now.
    git -C "%REPO_DIR%" init || goto :fail
)
git -C "%REPO_DIR%" fetch --no-tags "%UPDATE_REPO%" main || goto :fail
git -C "%REPO_DIR%" reset --hard FETCH_HEAD || goto :fail

rem Remove old temporary JAR-injection patches and temp directories from earlier patch attempts.
if exist "%REPO_DIR%\patches" rd /s /q "%REPO_DIR%\patches" >nul 2>nul
for /d %%D in ("%REPO_DIR%\.tmp-*") do (
    if exist "%%~fD" rd /s /q "%%~fD" >nul 2>nul
)

set "EXTERNAL_COMPOSE="
set "EXTERNAL_DIR="

rem Prefer a deployment compose file beside the repository, e.g. D:\Wise_G\docker-compose.yml.
for %%I in ("%REPO_DIR%\..") do set "PARENT_DIR=%%~fI"
for %%F in (docker-compose.yml docker-compose.yaml compose.yml compose.yaml) do (
    if not defined EXTERNAL_COMPOSE if exist "%PARENT_DIR%\%%F" (
        set "EXTERNAL_COMPOSE=%PARENT_DIR%\%%F"
        set "EXTERNAL_DIR=%PARENT_DIR%"
    )
)

rem Also support putting this BAT in the deployment directory while keeping source in .\dynamic-bot.
if /I not "%SCRIPT_DIR%"=="%REPO_DIR%" (
    for %%F in (docker-compose.yml docker-compose.yaml compose.yml compose.yaml) do (
        if not defined EXTERNAL_COMPOSE if exist "%SCRIPT_DIR%\%%F" (
            set "EXTERNAL_COMPOSE=%SCRIPT_DIR%\%%F"
            set "EXTERNAL_DIR=%SCRIPT_DIR%"
        )
    )
)

echo [2/4] Checking signed URL configuration...
if exist "%REPO_DIR%\config\main.yml" (
    findstr /I /C:"http://dynamic-bot:2233" "%REPO_DIR%\config\main.yml" >nul 2>nul
    if errorlevel 1 (
        echo [WARN] config\main.yml does not appear to contain http://dynamic-bot:2233
        echo        For Docker-to-Docker OneBot delivery, set signedUrl.publicBaseUrl to that address.
    )
)

echo [3/4] Building dynamic-bot from current source...
if defined EXTERNAL_COMPOSE (
    pushd "%EXTERNAL_DIR%" || goto :fail
    docker compose -f "%EXTERNAL_COMPOSE%" build --pull dynamic-bot
    if errorlevel 1 (
        popd
        goto :fail
    )

    echo [4/4] Recreating dynamic-bot container...
    docker compose -f "%EXTERNAL_COMPOSE%" up -d --no-deps dynamic-bot
    if errorlevel 1 (
        popd
        goto :fail
    )
    docker compose -f "%EXTERNAL_COMPOSE%" ps dynamic-bot
    popd
) else (
    if not exist "%REPO_DIR%\docker-compose.yml" (
        echo [ERROR] No deployment compose file was found.
        goto :fail
    )
    if not exist "%REPO_DIR%\docker-compose.source.yml" (
        echo [ERROR] docker-compose.source.yml is missing.
        goto :fail
    )
    pushd "%REPO_DIR%" || goto :fail
    docker compose -f docker-compose.yml -f docker-compose.source.yml build --pull dynamic-bot
    if errorlevel 1 (
        popd
        goto :fail
    )

    echo [4/4] Recreating dynamic-bot container...
    docker compose -f docker-compose.yml -f docker-compose.source.yml up -d --no-deps --pull never dynamic-bot
    if errorlevel 1 (
        popd
        goto :fail
    )
    docker compose -f docker-compose.yml -f docker-compose.source.yml ps dynamic-bot
    popd
)

echo.
echo [OK] dynamic-bot was updated from half-drop/dynamic-bot main and rebuilt from source.
echo Existing config/data/logs are not deleted; only tracked source files are reset to GitHub main.
echo.
docker logs --tail 30 dynamic-bot 2>nul
echo.
pause
exit /b 0

:fail
echo.
echo [FAILED] Update stopped. Existing config/data were not deleted.
echo.
pause
exit /b 1
