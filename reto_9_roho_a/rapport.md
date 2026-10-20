# Rapport – Reto 9 : Accediendo al GPS

**Application :** « Puntos de Interés », une carte des points d'intérêt autour de l'utilisateur
**Projet :** `reto_9_roho_a`, package `com.example.reto_9_roho_a`
**Stack :** Kotlin, Jetpack Compose (Material 3), osmdroid (OpenStreetMap), Google Play Services Location, API Overpass
**Langue de l'UI :** espagnol, comme dans les retos précédents

---

## 1. Exigences du PDF et réponses apportées

| Exigence du PDF | Implémentation |
|---|---|
| Partir des coordonnées GPS de l'utilisateur | `LocationProvider` (`FusedLocationProviderClient.getCurrentLocation`), bouton « Mi ubicación » |
| …ou d'une adresse | Champ « Buscar dirección » : `GeocodingRepository` (`Geocoder` Android, puis Nominatim en secours) |
| Afficher sur une carte les points d'intérêt proches (hôpital, lieu touristique…) | `PoiMap` (osmdroid) + `OverpassClient` ; 5 catégories dans `PoiCategory` |
| Rayon en kilomètres, configuré par les **préférences** | `SettingsStore` (SharedPreferences), écran `SettingsActivity` (slider 1–50 km) |
| Indiquer le nom de chaque point d'intérêt | Titre du marqueur (info-bulle au toucher) et liste sous la carte |

## 2. Lancer l'application

```
./run.bat
```

Compile, installe l'APK debug sur le téléphone branché, puis relance l'application.
L'application a besoin d'Internet (tuiles de la carte et requêtes Overpass) et de la permission de localisation.

## 3. Architecture

```
app/src/main/java/com/example/reto_9_roho_a/
├── MainActivity.kt          Écran carte : recherche, chips de catégories, liste des POI
├── data/
│   ├── Poi.kt               Modèle + formatage de la distance
│   ├── PoiCategory.kt       Enum des catégories (libellé, couleur, filtres Overpass)
│   ├── OverpassClient.kt    Requête Overpass (HttpURLConnection) et parsing JSON
│   ├── PoiRepository.kt     POI autour d'un point (suspend, Dispatchers.IO)
│   ├── GeocodingRepository.kt  Adresse → coordonnées
│   └── SettingsStore.kt     Préférences : rayon et catégories
├── location/LocationProvider.kt  Position GPS actuelle
├── viewmodel/MapState.kt    État de l'écran carte (StateFlow)
└── ui/
    ├── PoiMap.kt            MapView osmdroid dans Compose (AndroidView)
    ├── SettingsActivity.kt  Écran des préférences
    └── theme/               Couleurs, typographie, MaterialTheme
```

Découpage identique aux retos précédents : **Activity (Compose) → ViewModel (`AndroidViewModel` + `StateFlow`) → Repository**.

## 4. Choix d'implémentation

- **osmdroid plutôt que Google Maps :** aucune clé API ni facturation. Le `userAgentValue` est défini
  (`HTTP_USER_AGENT`) et le cache est placé dans `cacheDir`, donc sans permission de stockage.
- **Fournisseur de tuiles : Esri World Street Map, pas le serveur OSM par défaut.** Constaté sur le téléphone :
  `tile.openstreetmap.org` renvoyait des tuiles « 403 Access blocked » depuis le réseau 4G, même avec un user agent
  propre (le même serveur répond 200 depuis le PC). CARTO Voyager a été essayé et exige maintenant une clé API
  (filigrane « API KEY REQUIRED »). Esri n'a besoin d'aucune clé ; son URL est en ordre `z/y/x`, d'où une
  `OnlineTileSourceBase` avec `getTileURLString` redéfini. L'attribution est affichée par `CopyrightOverlay`.
- **Overpass :** requête `nwr(around:<mètres>,lat,lon)[filtre]` par catégorie, avec `out center tags` pour obtenir
  un point pour les bâtiments et zones (way/relation). Les éléments sans `name` sont ignorés.
  Trois serveurs sont essayés dans l'ordre (20 s de lecture chacun). Résultat limité à 40 POI **par catégorie**
  (et non 150 au total, qui concentrait tout autour de l'utilisateur), trié par distance.
- **Préférences :** le rayon (1–50 km, 5 par défaut) et les catégories sont lus à chaque `onResume`.
  S'ils ont changé et qu'un centre existe, la recherche est relancée automatiquement.
- **Chips de la carte :** elles masquent ou affichent des catégories sans nouvelle requête réseau.
- **Permissions :** demandées au premier lancement. Refus → message invitant à utiliser l'adresse.
  GPS désactivé → message avec un bouton « Activar » ouvrant les réglages de localisation.
- **Cycle de vie de la carte :** `onResume` / `onPause` du `MapView` sont relayés depuis le lifecycle, `onDetach` à la destruction.

## 5. Limites connues

- Les données viennent d'OpenStreetMap : un lieu sans `name` n'apparaît pas.
- L'API Overpass publique peut répondre lentement, refuser (HTTP 429) ou être en panne ; l'application affiche alors
  un message d'erreur (observé pendant les tests : 502 et timeouts sur plusieurs miroirs). Relancer avec « Mi ubicación ».
- Pas de tests unitaires du parseur : `org.json` et `Location` sont des stubs dans les tests JVM d'Android.
- Testé sur un Pixel 10a : carte, cercle de rayon, position, marqueurs et info-bulles avec le nom fonctionnent.
