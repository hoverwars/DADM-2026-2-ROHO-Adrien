@echo off
rem 1. Compiler et installer l'APK debug sur le telephone branche
rem    ("call" est necessaire, sinon le script s'arrete apres gradlew.bat)
call "%~dp0gradlew.bat" -p "%~dp0." :app:installDebug || exit /b 1

rem 2. Pousser le LLM (1,6 Go) sur le telephone s'il n'y est pas deja. Il n'est pas dans l'APK :
rem    l'app le lit dans son dossier externe. Telecharge une seule fois dans .\llm par gradle.
set "LLM_DIR=/sdcard/Android/data/com.example.demo_oral/files"
set "LLM_FILE=qwen2.5-1.5b-instruct.task"
adb shell ls %LLM_DIR%/%LLM_FILE% >nul 2>nul
if errorlevel 1 (
    call "%~dp0gradlew.bat" -p "%~dp0." :app:downloadLlm || exit /b 1
    adb shell mkdir -p %LLM_DIR%
    rem Copie vers un .part puis renommage, pour qu'un transfert interrompu ne passe pas pour complet
    adb push "%~dp0llm\%LLM_FILE%" %LLM_DIR%/%LLM_FILE%.part || exit /b 1
    adb shell mv %LLM_DIR%/%LLM_FILE%.part %LLM_DIR%/%LLM_FILE% || exit /b 1
)

rem 3. (Re)lancer l'app, force-stop d'abord pour etre sur de charger le nouveau build
adb shell am force-stop com.example.demo_oral
adb shell am start -n com.example.demo_oral/com.example.demo_oral.MainActivity
