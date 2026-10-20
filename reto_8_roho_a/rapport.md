# Rapport – Reto 8 : Almacenamiento con SQLite

**Application :** « Directorio TI », un annuaire d'entreprises de développement logiciel
**Projet :** `reto_8_roho_a`, package `com.example.reto_8_roho_a`
**Stack :** Kotlin, Jetpack Compose (Material 3), SQLite via `SQLiteOpenHelper`
**Langue de l'UI :** espagnol, comme dans les retos précédents

---

## 1. Exigences du PDF et réponses apportées

| Exigence du PDF | Implémentation |
|---|---|
| Fiche entreprise : nom, URL, téléphone, email, produits et services, classification | `data/Company.kt` et `data/Classification.kt`, table `companies` dans `CompanyContract.kt` |
| Classification : consultoría, desarrollo a la medida **et/ou** fábrica de software | Choix multiple : 3 colonnes booléennes (`is_consultoria`, `is_desarrollo_medida`, `is_fabrica_software`) |
| CRUD | `CompanyRepository` : `insert`, `getById` et `search`, `update`, `delete` |
| Lister les entreprises | `CompanyListActivity` (liste triée par nom) |
| Filtrer par nom et/ou classification | Barre de recherche plus chips de filtre, combinés dans une même requête SQL |
| Message de confirmation avant suppression | `ConfirmDeleteDialog` (AlertDialog), utilisé depuis la liste **et** depuis la fiche |

## 2. Lancer l'application

```
./run.bat
```

Le script compile, installe l'APK debug sur le téléphone ou l'émulateur branché, puis relance l'application.

> **Correction apportée à `run.bat` :** la ligne Gradle est maintenant `call gradlew.bat :app:installDebug`.
> Dans un fichier `.bat`, appeler un autre `.bat` **sans `call`** ne rend jamais la main : les commandes
> `adb ... am start` n'étaient donc jamais exécutées. Le `run.bat` de reto_7 a le même défaut.
> Le fichier doit aussi garder des fins de ligne **CRLF**, sinon cmd.exe l'interprète mal.

## 3. Architecture

```
app/src/main/java/com/example/reto_8_roho_a/
├── MainActivity.kt              Écran d'accueil (statistiques et accès rapides)
├── data/
│   ├── Company.kt               Modèle (data class)
│   ├── Classification.kt        Enum des 3 classifications et leur colonne SQL
│   ├── CompanyContract.kt       Schéma : noms de table et colonnes, CREATE TABLE
│   ├── CompanyDbHelper.kt       SQLiteOpenHelper (création, version, données d'exemple)
│   └── CompanyRepository.kt     Requêtes CRUD et recherche (suspend, Dispatchers.IO)
├── viewmodel/
│   ├── HomeState.kt             Compteurs de l'accueil
│   ├── CompanyListState.kt      Filtres, résultats, suppression avec confirmation
│   ├── CompanyDetailState.kt    Fiche et suppression
│   └── CompanyFormState.kt      Champs, validation, insertion ou mise à jour
└── ui/
    ├── CompanyListActivity.kt   Liste, recherche, filtres, FAB « Nueva empresa »
    ├── CompanyDetailActivity.kt Fiche, actions de contact, Editar et Eliminar
    ├── CompanyFormActivity.kt   Formulaire de création et d'édition
    ├── ContactIntents.kt        Ouverture du web, du composeur et du mail (Intents)
    ├── components/              Avatar, badges de classification, dialog de suppression
    └── theme/                   Couleurs, typographie, MaterialTheme
```

Le découpage est le même que dans les retos précédents :
**Activity (UI Compose) → ViewModel (`AndroidViewModel` + `StateFlow`) → Repository → SQLite**.

### Navigation (une Activity par écran)

```
MainActivity ──► CompanyListActivity ──► CompanyDetailActivity ──► CompanyFormActivity (édition)
     │                  │  (menu ⋮ : Editar / Eliminar)
     │                  └──► CompanyFormActivity (création, FAB)
     └──► CompanyFormActivity (création, « Registrar empresa »)
```

- Les données passent entre les écrans par des extras d'`Intent` : l'id de l'entreprise, ou le filtre initial.
  Chaque Activity expose un `newIntent(...)` dans son `companion object`, ce qui évite de répéter les clés.
- **Rafraîchissement :** chaque écran recharge ses données dans `onResume()`. Quand on revient d'un formulaire
  ou d'une suppression, la liste, la fiche et les statistiques sont donc à jour, sans mécanisme supplémentaire.
- Toucher une statistique de l'accueil (par exemple « Consultoría ») ouvre la liste **déjà filtrée** sur cette classification.

## 4. La base SQLite

### Schéma

```sql
CREATE TABLE companies (
    _id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                 TEXT NOT NULL,
    url                  TEXT NOT NULL DEFAULT '',
    phone                TEXT NOT NULL DEFAULT '',
    email                TEXT NOT NULL DEFAULT '',
    products_services    TEXT NOT NULL DEFAULT '',
    is_consultoria       INTEGER NOT NULL DEFAULT 0,
    is_desarrollo_medida INTEGER NOT NULL DEFAULT 0,
    is_fabrica_software  INTEGER NOT NULL DEFAULT 0
)
```

Fichier sur l'appareil : `/data/data/com.example.reto_8_roho_a/databases/directorio_empresas.db`
(consultable dans Android Studio via *App Inspection → Database Inspector*).

### Choix d'implémentation

- **`SQLiteOpenHelper` plutôt que Room :** c'est l'API présentée dans le tutoriel du reto. Le SQL reste visible,
  ce qui colle à l'intitulé « Almacenamiento con SQLite ».
- **3 colonnes booléennes pour la classification :** une entreprise peut cumuler les classifications, et il n'y en a
  que 3, fixes. Une table de liaison serait plus « normalisée » mais demanderait des JOIN pour rien.
  Chaque valeur de l'enum `Classification` connaît le nom de sa colonne (`column`), donc la conversion entre
  l'objet et la ligne SQL est une simple boucle sur `Classification.entries`.
- **Singleton** `CompanyDbHelper.getInstance(context)` : une seule connexion pour toute l'application, construite
  sur `applicationContext` pour éviter les fuites d'Activity.
- **Données d'exemple :** 5 entreprises **fictives** (domaines `.example`, numéros en 555) sont insérées dans
  `onCreate()`, donc **uniquement à la première création de la base**. Pour les remettre, il faut désinstaller
  l'application ou vider ses données.
- **`onUpgrade` :** pour l'instant il supprime et recrée la table (il n'y a pas encore de migration à écrire).
  Changer `DATABASE_VERSION` réinitialise donc la base.
- **Thread :** toutes les fonctions du repository sont `suspend` et exécutées sur `Dispatchers.IO`.
  L'UI ne bloque jamais sur une requête.

### Requête de recherche (filtres)

`CompanyRepository.search(nameQuery, classifications)` construit dynamiquement le `WHERE` :

- **Nom :** `name LIKE ? ESCAPE '\'` avec l'argument `%texte%`. La requête est **paramétrée**, donc il n'y a pas
  d'injection SQL possible. Les caractères `%` et `_` saisis par l'utilisateur sont échappés pour être cherchés
  littéralement. `LIKE` ignore la casse pour les lettres ASCII, mais pas pour les lettres accentuées
  (« Á » et « á » sont considérées comme différentes).
- **Classifications :** `(is_consultoria = 1 OR is_fabrica_software = 1 ...)`. Une entreprise s'affiche si elle a
  **au moins une** des classifications sélectionnées (logique OU entre les chips).
- **Nom et classifications** sont combinés par un `AND`.
- Les résultats sont triés par `name COLLATE NOCASE ASC`.

Pour les statistiques de l'accueil, une seule requête suffit :
`SELECT COUNT(*), SUM(is_consultoria), SUM(is_desarrollo_medida), SUM(is_fabrica_software) FROM companies`.

## 5. Validation du formulaire (`CompanyFormState`)

| Champ | Règle |
|---|---|
| Nom | Obligatoire |
| Classification | Au moins une, obligatoire |
| URL | Facultative ; si elle est remplie, elle doit respecter `Patterns.WEB_URL` |
| Téléphone | Facultatif ; si rempli, `Patterns.PHONE` |
| Email | Facultatif ; si rempli, `Patterns.EMAIL_ADDRESS` |
| Produits et services | Texte libre multiligne |

Les espaces en début et fin sont retirés avant l'enregistrement. L'erreur d'un champ s'affiche sous ce champ
et disparaît dès que l'utilisateur le modifie.

## 6. Interface (style moderne, Material 3)

- **Thème personnalisé** indigo et vert-bleu, en clair et en sombre. Le *dynamic color* d'Android 12+ est désactivé
  pour garder une identité visuelle constante. Affichage *edge-to-edge*.
- **Un code couleur et une icône par classification** : violet pour consultoría, vert-bleu pour desarrollo a la medida,
  orange pour fábrica de software. On les retrouve dans les chips, les badges, les statistiques et le formulaire.
- **Avatar avec initiales** : sa couleur est déduite du nom de l'entreprise, elle reste donc la même à chaque affichage.
- **Accueil** : carte en dégradé, 4 tuiles de statistiques cliquables, boutons « Ver directorio » et
  « Registrar empresa ». La mise en page est adaptée au mode paysage.
- **Liste** : recherche instantanée (chaque frappe relance la requête et annule la précédente), chips de filtre,
  compteur de résultats et « Limpiar filtros », états vides différents selon qu'un filtre est actif ou non,
  menu ⋮ par carte, Snackbar après une suppression.
- **Fiche** : actions de contact. Toucher l'URL ouvre le navigateur, le téléphone ouvre le **composeur**
  (`ACTION_DIAL`, donc sans appel direct et sans permission à demander), l'email ouvre l'application mail
  (`ACTION_SENDTO mailto:`). Si aucune application ne peut ouvrir l'Intent, un Toast l'indique.
- **Formulaire** : icônes, claviers adaptés (URL, téléphone, email), classifications sous forme de cartes cochables,
  `imePadding()` pour que le clavier ne cache pas les champs.

## 7. Points utiles à savoir

- **Rotation :** les ViewModels survivent à la rotation. `CompanyFormState.init()` et
  `CompanyListState.initClassificationFilter()` ne s'exécutent qu'une fois, pour ne pas écraser ce que
  l'utilisateur a saisi.
- **Suppression depuis la fiche :** après confirmation, un Toast s'affiche puis la fiche se ferme. Si l'entreprise
  n'existe plus quand on revient sur une fiche, celle-ci se ferme d'elle-même.
- **Logs :** les tags `CompanyDbHelper`, `CompanyRepository` et `CompanyListState` tracent la création de la base
  et chaque opération CRUD (`adb logcat -s CompanyRepository`).
- **Configuration Gradle :** reprise de reto_7 sans Firebase (Gradle 9.5, AGP 9.3.2, Kotlin 2.3.21, Compose BOM
  2026.06.00, compileSdk et targetSdk 37, minSdk 24). Toutes les dépendances passent par
  `gradle/libs.versions.toml`.

## 8. Tests manuels effectués (émulateur Pixel)

1. Accueil : les statistiques correspondent aux données d'exemple (5 entreprises, 3, 4, 2).
2. Liste : 5 entreprises triées ; le filtre « Consultoría » en laisse 3 et fait apparaître « Limpiar filtros ».
3. Menu ⋮ puis Eliminar : la boîte de confirmation s'affiche, et Cancelar ne supprime rien.
4. Fiche : les coordonnées et les produits s'affichent, avec Editar et Eliminar.
5. Édition : cocher « Fábrica de software » puis Guardar affiche le Toast « actualizada » ; la fiche se recharge
   avec le nouveau badge, et le log indique `Empresa actualizada: id=2, filas=1`.
