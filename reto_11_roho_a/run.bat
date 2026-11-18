@echo off
rem 1. Compiler et installer l'APK debug sur le telephone branche
rem    ("call" est necessaire, sinon le script s'arrete apres gradlew.bat)
call "%~dp0gradlew.bat" "-Dorg.gradle.java.home=%JAVA_HOME%" -p "%~dp0." :app:installDebug || exit /b 1

rem 2. Pousser le LLM (Gemma 4 E2B, 2,6 Go) sur le telephone s'il n'y est pas deja. Il n'est pas dans
rem    l'APK : l'app le lit dans son dossier externe. Telecharge une seule fois dans .\llm par gradle.
set "LLM_DIR=/sdcard/Android/data/com.example.reto_11_roho_a/files"
call :push gemma-4-E2B-it.litertlm || exit /b 1

rem 3. (Re)lancer l'app, force-stop d'abord pour etre sur de charger le nouveau build
adb shell am force-stop com.example.reto_11_roho_a
adb shell am start -n com.example.reto_11_roho_a/com.example.reto_11_roho_a.MainActivity
exit /b 0

:push
adb shell ls %LLM_DIR%/%1 >nul 2>nul
if not errorlevel 1 exit /b 0
call "%~dp0gradlew.bat" "-Dorg.gradle.java.home=%JAVA_HOME%" -p "%~dp0." :app:downloadLlm || exit /b 1
adb shell mkdir -p %LLM_DIR%
rem Copie vers un .part puis renommage, pour qu'un transfert interrompu ne passe pas pour complet
adb push "%~dp0llm\%1" %LLM_DIR%/%1.part || exit /b 1
adb shell mv %LLM_DIR%/%1.part %LLM_DIR%/%1 || exit /b 1
exit /b 0
