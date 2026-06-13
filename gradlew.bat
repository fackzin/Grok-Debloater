@rem Windows prompt launcher for local builds
@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    gradle %*
) else (
    echo Erro: Gradle local nao encontrado no PATH de variaveis de ambiente Windows. Configure sua instalacao de SDK/Studio.
    exit /b 1
)
