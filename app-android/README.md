# App Android Carburanti

Progetto multi-modulo per l'app Android che legge i prezzi carburante da Turso.

## Moduli

- `:data` — modelli e Repository (lettura da Turso). Kotlin/JVM puro con `HttpFuelRepository`.
- `:app` — UI telefono (Compose): lista + mappa MapLibre + dettaglio con grafico storico.
- `:car` — superficie Android Auto (Car App Library). Da aggiungere nello STEP C3.

## Strategia data layer

La spec chiede prima l'**opzione A** (embedded replica via `tech.turso.libsql`). Il SDK e' Android-only
(richiede AGP), quindi non e' eseguibile dal CLI desktop. Ho applicato il ripiego previsto dalla spec:
implemento prima l'**opzione B** (HTTP via `/v2/pipeline`), che gira sia su JVM (CLI) sia su Android.
La `LibsqlFuelRepository` arrivera' nello STEP C2 dietro la stessa interfaccia `FuelRepository`.

## Setup ambiente

1. Installa **Android Studio** (Hedgehog o successivo): https://developer.android.com/studio
   Include JDK 17 e Gradle, sufficienti per buildare il modulo `:data`.
2. Apri questa cartella `app-android/` come progetto in Android Studio.
   Al primo "Sync" Android Studio scarica le dipendenze e genera il wrapper Gradle (`gradlew`).
3. Verifica nel terminale di Android Studio (View → Tool Windows → Terminal):
   ```
   ./gradlew :data:compileKotlin
   ```

## Esecuzione del CLI (acceptance test STEP C1)

Imposta le env var con il token di **sola lettura** Turso (file `.turso_tokens` nella root del repo):

PowerShell:
```powershell
$env:TURSO_DATABASE_URL = "libsql://carburanti-dallariva93.aws-eu-west-1.turso.io"
$env:TURSO_RO_TOKEN     = (Get-Content ..\.turso_tokens).Trim()
./gradlew :data:run
```

Output atteso: tabella con i distributori piu' economici per GPL self entro 10 km da Trento (default).

Argomenti personalizzati:
```powershell
./gradlew :data:run --args="46.0664 11.1257 15 Benzina true"
# lat lon raggioKm carburante self
```

## App telefono (STEP C2)

Il modulo `:app` mostra:
- **Filtri**: scelta carburante + modalita self/servito + ordinamento per **prezzo** o **distanza**.
  Default self per tutti i carburanti; GPL e Metano (in Italia erogati solo serviti) passano
  automaticamente a **servito** e disabilitano il chip Self.
- **Lista** dei distributori piu' economici vicini alla posizione attuale,
  con pallino colorato per prezzo, comune, distanza e data.
- **Mappa** MapLibre con un marker per distributore, colorato per prezzo (verde = economico,
  rosso = caro) e il pallino della **posizione attuale**. Tap su un marker o su una riga ->
  **dettaglio** con il grafico dello storico.
- **Impianti possibilmente chiusi**: MIMIT non espone uno stato di chiusura, ma gli impianti chiusi
  spariscono dal registro attivo. L'app usa `impianti.updated` (ultimo run in cui l'impianto era nel
  registro) e segnala con un badge quelli fermi da >= 14 giorni rispetto all'ultimo run del dataset
  (non rispetto a "oggi", per evitare falsi positivi da pipeline ferma). E' un'euristica indicativa,
  non una certezza.

La mappa usa di default tile raster **OpenStreetMap** (niente API key). Per cambiare provider
imposta `MAP_TILES_URL` (template XYZ `{z}/{x}/{y}`) oppure, per uno stile vettoriale completo,
`MAP_STYLE_URL` (es. MapTiler con chiave) in `local.properties`. Vedi `local.properties.example`.

Tutti i dati passano dal `FuelRepository` di `:data`: la UI non parla mai direttamente con Turso.

### Configurazione (token di sola lettura)

URL e token NON sono nel sorgente versionato: si leggono da `local.properties` (git-ignored) e
finiscono in `BuildConfig`. Setup:

```bash
cp local.properties.example local.properties
# poi compila TURSO_DATABASE_URL e TURSO_RO_TOKEN
```

(Android Studio aggiunge anche `sdk.dir` allo stesso file al primo sync.)

### Build e run su device/emulatore

1. Apri `app-android/` in Android Studio e lascia completare il Gradle sync.
2. Collega un dispositivo reale (sideload via USB) o avvia un emulatore con Google Play.
3. Run della configurazione **app**, oppure da terminale:
   ```
   ./gradlew :app:installDebug
   ```
4. All'avvio l'app chiede il permesso di posizione; se negato usa Trento come fallback.

Verifica accettazione: lista e mappa popolate con dati reali da Turso; tap su un distributore
apre il dettaglio con la serie storica.
