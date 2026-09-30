@echo off
rem 1. Compiler et installer l'APK debug sur le telephone branche
rem    ("call" est necessaire, sinon le script s'arrete apres gradlew.bat)
call "%~dp0gradlew.bat" -p "%~dp0." :app:installDebug || exit /b 1

rem 2. Pousser les modeles LLM sur le telephone s'ils n'y sont pas deja. Ils ne sont pas dans l'APK :
rem    l'app les lit dans son dossier externe. Telecharges une seule fois dans .\llm par gradle.
rem    - qwen : le LLM de conversation (1,6 Go)
rem    - functiongemma : le routeur d'outils (280 Mo)
set "LLM_DIR=/sdcard/Android/data/com.example.demo_oral/files"
call :push qwen2.5-1.5b-instruct.task || exit /b 1
call :push functiongemma-270m-mobile-actions.litertlm || exit /b 1

rem 3. (Re)lancer l'app, force-stop d'abord pour etre sur de charger le nouveau build
adb shell am force-stop com.example.demo_oral
adb shell am start -n com.example.demo_oral/com.example.demo_oral.MainActivity
exit /b 0

:push
adb shell ls %LLM_DIR%/%1 >nul 2>nul
if not errorlevel 1 exit /b 0
call "%~dp0gradlew.bat" -p "%~dp0." :app:downloadLlm || exit /b 1
adb shell mkdir -p %LLM_DIR%
rem Copie vers un .part puis renommage, pour qu'un transfert interrompu ne passe pas pour complet
adb push "%~dp0llm\%1" %LLM_DIR%/%1.part || exit /b 1
adb shell mv %LLM_DIR%/%1.part %LLM_DIR%/%1 || exit /b 1
exit /b 0
