:: 1. Compiler et installer l'APK debug sur le téléphone branché
./gradlew.bat :app:installDebug

:: 2. (Re)lancer l'app — force-stop d'abord pour être sûr de charger le nouveau build
adb shell am force-stop com.example.reto_2_roho_a
adb shell am start -n com.example.reto_2_roho_a/com.example.reto_2_roho_a.MainActivity