# Prompt de reprise – Reto 7 Triqui Online

Colle ce prompt au début de la nouvelle session (dossier de travail : `D:\dev\kotlin\DADM-2026-2-ROHO-Adrien\reto_7_roho_a`).

---

## Contexte

Projet d'école (Android / Kotlin / Jetpack Compose) : jeu de Tic Tac Toe ("Triqui"), copie de `reto_4`. Objectif : implémenter le **Reto 7** (PDF `Reto7 - TriquiOnline.pdf` à la racine) = mode multijoueur en ligne :
- 2 joueurs par partie, chacun sur son téléphone ;
- n'importe qui peut créer une partie ;
- au démarrage de ce mode, on voit la liste des parties disponibles ;
- on rejoint une partie existante ou on en crée une ;
- on ne peut jouer qu'une fois qu'un 2ᵉ joueur a rejoint ;
- service cloud obligatoire (Firebase) ; le prof jouera avec chaque étudiant.

Contraintes données par l'utilisateur : backend déployé sur Firebase (gratuit, plan Spark, rien n'était configuré), **UI en espagnol**, UI et façon de jouer cohérentes avec l'existant, rotation du téléphone possible sur la nouvelle page. Les réponses à l'utilisateur sont en français.
Règle mémoire : après toute modification de code, lancer `./run.bat` (installe et lance l'APK sur le téléphone branché).

## Ce qui a été fait

**Backend** (`backend/`) : Cloud Firestore + règles de sécurité (pas de Cloud Functions : plan Blaze payant).
- `backend/firestore.rules`, `firestore.indexes.json`, `firebase.json`, `.firebaserc` (projet par défaut `reto-7-aroho`).
- Collection `games/{id}` : `hostId, hostName, guestId, guestName, status (waiting|playing|finished|abandoned), board ("---------", X/O), turn, winner ("", X, O, D), scoreX, scoreO, round, createdAt, updatedAt`.
- Les règles n'autorisent que : créer (en attente) → rejoindre → jouer → nouvelle manche → abandonner ; suppression seulement si `waiting`/`abandoned`.
- `.gitignore` complété (`backend/.firebase/`, `firebase-debug.log`).

**Gradle** : plugin `com.google.gms.google-services` 4.5.0 (`libs.versions.toml`, `build.gradle.kts` racine et `app`), `firebase-bom:35.0.0` + `firebase-firestore`, permission `INTERNET` et 2 activities dans le manifest.

**Code Android** (`app/src/main/java/com/example/reto_7_roho_a/`) :
- `data/OnlineGame.kt` (modèle + parsing Firestore + logique de victoire), `data/OnlineGamesRepository.kt` (écoute des parties, création, join/jeu/rematch en transactions, leave), `data/OnlinePlayerPrefs.kt` (id aléatoire + nom du joueur, sans compte).
- `viewmodel/OnlineLobbyState.kt`, `viewmodel/OnlineGameState.kt` (créateur = X bleu, invité = O rouge ; sons ; quitte la partie dans `onCleared`).
- `ui/OnlineLobbyActivity.kt` (liste temps réel + champ nom + « CREAR PARTIDA »), `ui/OnlineGameActivity.kt` (mêmes composants que le mode local, portrait + paysage, plateau bloqué tant que personne n'a rejoint).
- `ClassicTicTacToeGameActivity.kt` : `ScoreRow`, `TurnIndicator`, `GameResult`, `NewGameButton` passés en `internal` avec paramètres optionnels (noms/texte).
- `MainActivity.kt` : nouveau bouton « Modo En línea », helper `MenuButton`, espacements réduits, version « 0.7 ».

**Firebase (CLI v15 installé via npm)** : login fait avec `aroho@unal.edu.co`. Projet `reto-7-aroho` créé par l'utilisateur dans la console (CGU acceptées). App Android enregistrée : package `com.example.reto_7_roho_a`, App ID `1:85140797334:android:4fed3434394214d24dcfb7`.

## Ce qui n'a PAS été fait (à faire à la reprise)

Les commandes shell ont été bloquées (erreur transitoire du classifieur de sécurité). Donc :
1. Générer `app/google-services.json` :
   `cd backend && firebase apps:sdkconfig ANDROID 1:85140797334:android:4fed3434394214d24dcfb7 --project reto-7-aroho --out ../app/google-services.json`
2. Créer la base Firestore : `firebase firestore:databases:create "(default)" --location nam5 --project reto-7-aroho` (ou via la console si l'API demande une activation ; vérifier aussi que l'API Firestore est activée).
3. Déployer les règles : `cd backend && firebase deploy --only firestore:rules --project reto-7-aroho`.
4. **Compiler** (`./gradlew.bat :app:assembleDebug`) : le code n'a jamais été compilé, corriger les erreurs éventuelles (imports, API Firebase/lifecycle, `viewModelFactory`/`initializer`, etc.).
5. Lancer `./run.bat` (téléphone branché, adb) puis tester à deux appareils/émulateurs : création, apparition dans la liste de l'autre, join, blocage avant join, tours, victoire/nul, « NUEVA PARTIDA », abandon, rotation écran.
6. Si l'auth CLI a expiré : `firebase login` puis `firebase login <code>`.

Points à vérifier : les règles Firestore (syntaxe, `diff().affectedKeys()`), le filtrage des parties en attente de plus de 15 min, l'absence d'identité vérifiée (limite acceptée : pas d'Auth), commits git à faire seulement sur demande de l'utilisateur.
