package com.dallariva.carburanti.data.cli

import com.dallariva.carburanti.data.HttpFuelRepository
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * CLI di accettazione per STEP C1.
 *
 * Stampa i distributori piu' economici per un dato carburante entro un raggio dalle coordinate.
 *
 * Configurazione (env vars, MAI hardcoded):
 *   TURSO_DATABASE_URL   es. libsql://carburanti-xxxx.turso.io
 *   TURSO_RO_TOKEN       token di SOLA LETTURA (creato in fase B)
 *
 * Argomenti posizionali (tutti opzionali, default = Trento centro, 10 km, GPL self):
 *   args[0] lat    (Double)   default 46.0664
 *   args[1] lon    (Double)   default 11.1257
 *   args[2] raggio (Km)       default 10.0
 *   args[3] carburante        default "GPL"   (LIKE %x%)
 *   args[4] self              default true    ("true" / "false")
 */
fun main(args: Array<String>) {
    val url = System.getenv("TURSO_DATABASE_URL")
    val token = System.getenv("TURSO_RO_TOKEN")
    if (url.isNullOrBlank() || token.isNullOrBlank()) {
        System.err.println("Errore: TURSO_DATABASE_URL e TURSO_RO_TOKEN devono essere impostate.")
        exitProcess(1)
    }

    val lat = args.getOrNull(0)?.toDoubleOrNull() ?: 46.0664
    val lon = args.getOrNull(1)?.toDoubleOrNull() ?: 11.1257
    val raggio = args.getOrNull(2)?.toDoubleOrNull() ?: 10.0
    val carburante = args.getOrNull(3) ?: "GPL"
    val self = args.getOrNull(4)?.toBooleanStrictOrNull() ?: true

    val repo = HttpFuelRepository(url, token)
    val results = runBlocking { repo.cheapestNearby(lat, lon, raggio, carburante, self) }

    if (results.isEmpty()) {
        println("Nessun distributore con '$carburante' (self=$self) entro $raggio km da ($lat, $lon).")
        return
    }

    println("Distributori piu' economici per '$carburante' (self=$self) entro $raggio km da ($lat, $lon):")
    println()
    println("%-32s  %-18s  %7s  %8s  %s".format("NOME", "COMUNE", "PREZZO", "DISTANZA", "AGGIORNATO"))
    println("-".repeat(95))
    results.take(20).forEach {
        println(
            "%-32s  %-18s  %7.3f  %7.1fkm  %s".format(
                it.distributore.nome.take(32),
                it.distributore.comune.take(18),
                it.prezzo.prezzo,
                it.distanzaKm,
                it.prezzo.dtComu,
            )
        )
    }
    if (results.size > 20) {
        println()
        println("... ${results.size - 20} ulteriori risultati troncati.")
    }
}
