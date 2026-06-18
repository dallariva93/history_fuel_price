# App Android Carburanti

Progetto multi-modulo per l'app Android che legge i prezzi carburante da Turso.

## Moduli

- `:data` — modelli e Repository (lettura da Turso). Per ora Kotlin/JVM puro con `HttpFuelRepository`.
- `:app` — UI telefono (Compose). Da aggiungere nello STEP C2.
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
