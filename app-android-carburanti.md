# App Android prezzi carburanti (telefono + Android Auto) — Spec

> Documento self-contained, implementation-ready. **Ultima fase** del progetto: presuppone che la pipeline dati (Pi → Turso) della Fase 2 sia già attiva e popolata. Vedi `storico-prezzi-carburanti-MIMIT.md`, §12.
> App **a uso personale** (non destinata al pubblico). Obiettivo: vedere i distributori più economici vicini, sul telefono e — soprattutto — in auto via Android Auto.

---

## 1. Obiettivo e ambito

- **Telefono:** mappa dei distributori locali colorati per prezzo, lista dei più economici nei dintorni, dettaglio con grafico dello storico.
- **Android Auto (in macchina):** una superficie minimale e sicura che mostra i distributori più economici vicini. Niente grafici, niente UI custom (vedi §5).
- **Sorgente dati:** Turso (la source of truth alimentata dal Pi). L'app **legge soltanto**.

Non-obiettivi: scrittura dati dall'app, account/login multiutente, pubblicazione sul Play Store come prodotto.

---

## 2. Decisioni tecniche

| Aspetto | Scelta | Motivo |
|---|---|---|
| Linguaggio | **Kotlin** | Standard Android; interop totale con Java (background dev Java). Pezzo di "conoscenza nuova" a basso rischio. |
| UI telefono | **Jetpack Compose** | UI dichiarativa moderna. |
| Mappa | **MapLibre Android SDK** | Riusa la dimestichezza con MapLibre GL; alternativa Google Maps Compose. |
| Superficie auto | **Car App Library** (`androidx.car.app`), categoria **POI** | Unica via supportata per Android Auto; i distributori sono "punti di interesse". |
| Lettura dati | **Embedded replica Turso** (preferita) o **HTTP + cache Room** (fallback) | Offline-first in auto vs robustezza. Vedi §4. |
| Cache locale | SQLite/Room sul device | Letture istantanee e funzionamento offline. |

---

## 3. Architettura dell'app (moduli)

Una sola app, tre moduli, per condividere il data layer tra telefono e auto:

```
:data   → repository + accesso a Turso (embedded replica o HTTP) + modelli (Distributore, Prezzo)
:app    → UI telefono (Compose): mappa, lista, dettaglio+grafico
:car    → superficie Android Auto: CarAppService + Session + Screen (template POI)
```

`:app` e `:car` dipendono entrambi da `:data` e ne riusano lo stesso repository. Nessuna logica dati duplicata.

---

## 4. Data layer: leggere da Turso

Lo schema lato Turso rispecchia quello del Pi (`impianti`, `prezzi`; vedi §6 del doc Pi). L'app fa solo `SELECT`.

**Opzione A — Embedded replica (preferita).**
Una copia SQLite locale sul device che si sincronizza da Turso. Letture locali istantanee, funziona offline (cruciale in auto). SDK Android ufficiale (`tech.turso.libsql`), **in technical preview**; per ora legato all'Android Gradle Plugin. Sync periodico o all'avvio.

**Opzione B — HTTP remoto + Room (fallback).**
L'app interroga Turso via client HTTP e mantiene una cache Room locale per offline/velocità. Più codice ma più maturo.

> **Raccomandazione:** parti con A (è l'obiettivo "conoscenza nuova" ed è perfetta per l'auto). Se incontri spigoli della preview, ripiega su B senza cambiare l'interfaccia del repository.

**Auth token:** all'app serve un token Turso di **sola lettura**. Va incorporato nell'APK: tecnicamente non è un vero segreto, ma **i dati sono open data pubblici**, quindi il rischio è nullo. Non mettere mai nell'app il token di scrittura del Pi.

**Query principali** (sul replica/cache locale):
- distributori entro un raggio dalla posizione attuale, con l'**ultimo** prezzo noto per carburante;
- ordinamento per prezzo crescente del carburante scelto (default GPL self);
- per il dettaglio: serie storica `prezzo`/`dt_comu` di un impianto+carburante.

---

## 5. Superficie Android Auto (la parte vincolata)

In auto **non si disegna UI custom**: si usano i **template** della Car App Library, pensati per non distrarre alla guida. Un'app prezzi-carburante ricade nella categoria **POI**, che dà accesso a `PlaceListMapTemplate` e `MapWithContentTemplate`. Vincoli: niente elementi animati, immagini molto limitate, liste corte, interazione minima, niente inserimento testo libero.

**Cosa mostra in auto (MVP):** `PlaceListMapTemplate` con i distributori più economici vicini — nome, comune, prezzo, distanza — su mappa con marker. Tap su una voce → opzionale avvio navigazione verso quel distributore (intent di navigazione). Pulsante di refresh via `OnContentRefreshListener`. Attivazione vocale ("trova distributori vicini") via App Actions, opzionale.

**Cosa NON va in auto:** grafici dello storico, filtri complessi, schermate dense. Lo storico resta sul telefono.

**Dichiarazione nel manifest** (categoria POI + permesso per il template mappa):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="androidx.car.app.MAP_TEMPLATES" />
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.INTERNET" />
  <application>
    <service
        android:name=".car.FuelCarAppService"
        android:exported="true">
      <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.POI" />
      </intent-filter>
    </service>
  </application>
</manifest>
```

**Scheletro minimo della superficie auto** (Kotlin, da espandere):

```kotlin
// :car
class FuelCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR   // uso personale; in produzione restringere
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = NearbyFuelScreen(carContext)
    }
}

class NearbyFuelScreen(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val stazioni = Repository.cheapestNearby(/* posizione, carburante */)  // da :data
        val list = ItemList.Builder().apply {
            stazioni.forEach { s ->
                addItem(
                    Row.Builder()
                        .setTitle("${s.nome} — ${s.prezzo} €")
                        .addText("${s.comune} • ${s.distanzaKm} km")
                        .build()
                )
            }
        }.build()
        return PlaceListMapTemplate.Builder()
            .setItemList(list)
            .setTitle("Carburante vicino")
            .setOnContentRefreshListener { invalidate() }
            .build()
    }
}
```

---

## 6. I due paletti operativi (leggere prima di stimare i tempi)

**1. Far girare l'app in auto (non solo nell'emulatore).**
- Sviluppo e collaudo: il **DHU (Desktop Head Unit)** sul PC emula lo schermo auto; ci provi tutto gratis.
- Auto vera: l'opzione sviluppatore "fonti sconosciute" di Android Auto **non si applica alle app Car App Library**. Per vederla sullo schermo della macchina serve, con ogni probabilità, pubblicarla su una **traccia di test interno/chiuso del Play Store** (account sviluppatore Google: costo una tantum). Resta privata, ma passa dal Play Store.
- L'**app-telefono**, invece, si installa via sideload senza problemi: l'attrito è solo sulla proiezione in auto.

**2. SDK Turso Android in preview.**
- L'embedded replica su Android è ufficiale ma giovane. Per uso personale va bene; tieni pronto il fallback HTTP+Room (§4-B) dietro la stessa interfaccia di repository, così il cambio è indolore.

---

## 7. Permessi

- `ACCESS_FINE_LOCATION` — distributori vicini (telefono e auto).
- `INTERNET` — sync con Turso.
- `androidx.car.app.MAP_TEMPLATES` — richiesto da `PlaceListMapTemplate`, altrimenti l'app va in crash all'uso del template.

---

## 8. Scope MVP dell'app vs nice-to-have

**MVP:**
- data layer su Turso (replica o HTTP) + modelli condivisi;
- telefono: lista "più economici vicino a me" + mappa con marker;
- auto: `PlaceListMapTemplate` con i più economici vicini + refresh;
- collaudo su DHU.

**Nice-to-have (dopo):**
- grafico storico sul telefono;
- navigazione al distributore dal tap in auto;
- attivazione vocale (App Actions);
- preferiti / soglia di alert.

---

## 9. Checklist

- [ ] Account sviluppatore Google creato (necessario per la traccia interna che abilita l'auto reale).
- [ ] DB Turso raggiungibile dall'app con token di **sola lettura**; schema `impianti`/`prezzi` popolato dal Pi.
- [ ] `:data` con repository funzionante (prima opzione A; se serve, B) e query "più economici vicini".
- [ ] `:app` telefono: lista + mappa, testata su dispositivo reale (sideload).
- [ ] `:car`: `CarAppService` + `NearbyFuelScreen`, testata sul **DHU**.
- [ ] Verifica permesso `MAP_TEMPLATES` presente (altrimenti crash sul template).
- [ ] Pubblicazione su traccia di **test interno** del Play Store e prova sullo schermo auto reale.

---

## 10. Fonti (verificare la doc corrente, queste API evolvono)

- Car App Library — overview e setup categorie: `https://developer.android.com/training/cars/apps`, `.../library/set-up-project`
- App POI (categoria, template, gas stations): `https://developer.android.com/training/cars/apps/poi`
- Codelab fondamentali (`MAP_TEMPLATES`, struttura :app/:data): `https://developer.android.com/codelabs/car-app-library-fundamentals`
- Test e DHU / limiti del sideload: `https://developer.android.com/training/cars/testing`
- Turso SDK Android (preview, embedded replica): `https://docs.turso.tech/sdk/kotlin/reference`, `https://github.com/tursodatabase/libsql-android`

---

## 11. Prompt per l'agente (staged)

Da usare quando si arriva a questa fase. Allegare all'agente **questo documento**. Procedere uno step alla volta.

### Prompt 0 — Contesto

```
Stai costruendo un'app Android personale in Kotlin seguendo la spec `app-android-carburanti.md`
che ti allego. Leggila e consideralo la fonte di verità. È l'ultima fase di un progetto la cui
pipeline dati (Pi → Turso) è già attiva: l'app LEGGE soltanto da Turso.

Regole:
- Kotlin + Jetpack Compose per il telefono; Car App Library (categoria POI) per Android Auto.
- Tre moduli: :data (repository + accesso Turso + modelli), :app (UI telefono), :car (Android Auto).
  :app e :car riusano lo stesso repository di :data. Nessuna duplicazione del data layer.
- App a uso personale: niente login, niente scrittura, token Turso di sola lettura.
- MVP-first, pragmatico: niente astrazioni inutili.
- Procedi UNO STEP ALLA VOLTA: a fine step mostrami i file e come verificarli, poi aspetta l'ok.
```

### Prompt 1 — Step 1: modulo :data (repository su Turso)

```
STEP 1. Crea il modulo :data: modelli (Distributore, Prezzo), e un Repository che legge da Turso.
Implementa PRIMA l'opzione A (embedded replica, SDK tech.turso.libsql); se la preview dà problemi
bloccanti, ripiega su B (HTTP + cache Room) MANTENENDO la stessa interfaccia pubblica del Repository.
Esponi almeno: cheapestNearby(lat, lon, raggioKm, carburante) e history(impiantoId, carburante).
Token Turso di sola lettura da configurazione, non hardcoded nel codice sorgente versionato.

Accettazione: un piccolo test/cli che stampa i distributori più economici entro un raggio da
coordinate date, leggendo dati reali da Turso.
```

### Prompt 2 — Step 2: :app telefono (Compose)

```
STEP 2. Crea il modulo :app: schermata principale con (a) lista dei distributori più economici
vicini alla posizione attuale per il carburante scelto (default GPL self) e (b) mappa MapLibre
con marker colorati per prezzo. Tap su un distributore -> schermata dettaglio con la serie storica
(grafico). Usa il Repository di :data, niente accesso dati diretto qui.

Accettazione: su un dispositivo/emulatore reale, la lista e la mappa mostrano dati reali da Turso;
il dettaglio mostra lo storico di un distributore.
```

### Prompt 3 — Step 3: :car Android Auto (template POI)

```
STEP 3. Crea il modulo :car: CarAppService (categoria POI), Session e una Screen che usa
PlaceListMapTemplate per mostrare i distributori più economici vicini (nome, prezzo, comune,
distanza) con un OnContentRefreshListener. Dichiara nel manifest la categoria POI e il permesso
androidx.car.app.MAP_TEMPLATES. Riusa il Repository di :data. Niente grafici o UI custom.

Accettazione: l'app compare e funziona nel DHU (Desktop Head Unit); la lista si popola da dati
reali e il refresh aggiorna. Spiegami come avviare il DHU per provarla.
```

### Prompt 4 — Step 4: distribuzione per l'auto reale

```
STEP 4. Guidami nella pubblicazione su una traccia di TEST INTERNO del Play Store, necessaria
per far girare la superficie Android Auto sullo schermo dell'auto vera (il sideload non basta per
le app Car App Library). Elenca i passi: requisiti dell'account sviluppatore, preparazione
dell'APK/AAB firmato, creazione della traccia interna, come aggiungere il mio account tester,
come installare dalla traccia e verificare in macchina. Niente di più del necessario per l'uso
personale.
```
