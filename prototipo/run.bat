:: 1. Compiler et installer l'APK debug sur le téléphone branché
./gradlew.bat :app:installDebug

:: 2. (Re)lancer l'app — force-stop d'abord pour être sûr de charger le nouveau build
adb shell am force-stop com.example.prototipo
adb shell am start -n com.example.prototipo/com.example.prototipo.MainActivity