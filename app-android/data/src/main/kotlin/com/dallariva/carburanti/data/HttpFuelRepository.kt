package com.dallariva.carburanti.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Soglia in giorni oltre la quale un impianto, assente dal registro attivo, viene segnalato come
 * [StatoImpianto.NON_AGGIORNATO] ("possibilmente chiuso"). Misurata rispetto all'ultimo run noto
 * del dataset, non rispetto a oggi.
 */
const val STALE_DAYS = 14

/**
 * Implementazione di [FuelRepository] basata sull'API HTTP Hrana di Turso (`/v2/pipeline`).
 *
 * Vantaggi: zero codice nativo, gira ovunque (JVM, Android). Usata come fallback all'embedded
 * replica e come backend per il CLI di test.
 *
 * @param databaseUrl URL del DB Turso, formato `libsql://<host>` o `https://<host>`.
 * @param authToken Token di SOLA LETTURA. Letto da configurazione, mai hardcoded nel sorgente.
 */
class HttpFuelRepository(
    databaseUrl: String,
    private val authToken: String,
) : FuelRepository {

    private val endpoint: String = run {
        val base = if (databaseUrl.startsWith("libsql://"))
            "https://" + databaseUrl.removePrefix("libsql://")
        else databaseUrl
        base.trimEnd('/') + "/v2/pipeline"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun cheapestNearby(
        lat: Double,
        lon: Double,
        raggioKm: Double,
        carburante: String,
        self: Boolean,
    ): List<DistributoreConPrezzo> = withContext(Dispatchers.IO) {
        // Pre-filtro lato DB con bounding box: ~111 km/grado lat, lon scalata per cos(lat).
        val dLat = raggioKm / 111.0
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.001)
        val dLon = raggioKm / (111.0 * cosLat)

        val sql = """
            WITH latest AS (
              SELECT impianto_id, carburante, self, MAX(dt_comu) AS max_dt
              FROM prezzi
              WHERE carburante LIKE ? AND self = ?
              GROUP BY impianto_id, carburante, self
            )
            SELECT i.id, i.nome, i.gestore, i.bandiera, i.tipo, i.indirizzo,
                   i.comune, i.provincia, i.lat, i.lon, i.updated,
                   p.carburante, p.self, p.prezzo, p.dt_comu
            FROM impianti i
            JOIN latest l ON l.impianto_id = i.id
            JOIN prezzi p ON p.impianto_id = l.impianto_id
                          AND p.carburante = l.carburante
                          AND p.self       = l.self
                          AND p.dt_comu    = l.max_dt
            WHERE i.lat BETWEEN ? AND ?
              AND i.lon BETWEEN ? AND ?
        """.trimIndent()

        val rows = executeQuery(
            sql,
            listOf(
                "%$carburante%",
                if (self) 1 else 0,
                lat - dLat, lat + dLat,
                lon - dLon, lon + dLon,
            )
        )

        // Data dell'ultimo run noto del dataset = MAX(updated) tra le righe restituite.
        // Misurare la staleness rispetto a questo (non a "oggi") evita falsi positivi quando la
        // pipeline resta ferma qualche giorno: tutti gli impianti condividono lo stesso max.
        val datasetMax = rows.mapNotNull { it[10].asString() }.maxOrNull()

        // Refinement haversine + sort per prezzo crescente.
        rows.mapNotNull { r ->
            val d = Distributore(
                id = r[0].asLong(),
                nome = r[1].asString().orEmpty(),
                gestore = r[2].asString(),
                bandiera = r[3].asString(),
                tipo = r[4].asString(),
                indirizzo = r[5].asString(),
                comune = r[6].asString().orEmpty(),
                provincia = r[7].asString().orEmpty(),
                lat = r[8].asDouble(),
                lon = r[9].asDouble(),
                updated = r[10].asString(),
            )
            val p = Prezzo(
                carburante = r[11].asString().orEmpty(),
                self = r[12].asLong() == 1L,
                prezzo = r[13].asDouble(),
                dtComu = r[14].asString().orEmpty(),
            )
            val dist = haversineKm(lat, lon, d.lat, d.lon)
            if (dist > raggioKm) return@mapNotNull null

            val giorni = daysBetween(d.updated, datasetMax)
            val stato = if (giorni != null && giorni >= STALE_DAYS)
                StatoImpianto.NON_AGGIORNATO else StatoImpianto.ATTIVO
            DistributoreConPrezzo(d, p, dist, giorni, stato)
        }.sortedBy { it.prezzo.prezzo }
    }

    override suspend fun history(
        impiantoId: Long,
        carburante: String,
        self: Boolean,
    ): List<Prezzo> = withContext(Dispatchers.IO) {
        val rows = executeQuery(
            """
            SELECT carburante, self, prezzo, dt_comu
            FROM prezzi
            WHERE impianto_id = ? AND carburante LIKE ? AND self = ?
            ORDER BY dt_comu ASC
            """.trimIndent(),
            listOf(impiantoId, "%$carburante%", if (self) 1 else 0)
        )
        rows.map { r ->
            Prezzo(
                carburante = r[0].asString().orEmpty(),
                self = r[1].asLong() == 1L,
                prezzo = r[2].asDouble(),
                dtComu = r[3].asString().orEmpty(),
            )
        }
    }

    /** Esegue una singola SELECT e restituisce la lista delle righe come liste di valori Hrana. */
    private fun executeQuery(sql: String, args: List<Any?>): List<List<JsonElement>> {
        val body = buildJsonObject {
            putJsonArray("requests") {
                addJsonObject {
                    put("type", "execute")
                    putJsonObject("stmt") {
                        put("sql", sql)
                        put("args", JsonArray(args.map { encodeArg(it) }))
                    }
                }
                addJsonObject { put("type", "close") }
            }
        }
        val response = post(body).jsonObject
        val results = response["results"]?.jsonArray
            ?: error("Risposta Turso senza campo 'results': $response")
        val first = results[0].jsonObject
        val type = first["type"]?.jsonPrimitive?.content
        if (type != "ok") error("Errore Turso: $first")
        val rows = first["response"]!!.jsonObject["result"]!!.jsonObject["rows"]!!.jsonArray
        return rows.map { it.jsonArray.toList() }
    }

    private fun post(body: JsonObject): JsonElement {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $authToken")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.parseToJsonElement(text)
        } catch (e: Exception) {
            val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            throw RuntimeException("Turso HTTP ${conn.responseCode}: ${err ?: e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    /** Codifica un valore Kotlin nel formato args di Hrana. */
    private fun encodeArg(value: Any?): JsonObject = buildJsonObject {
        when (value) {
            null -> {
                put("type", "null")
                put("value", JsonNull)
            }
            is Boolean -> {
                put("type", "integer")
                put("value", if (value) "1" else "0")
            }
            is Int -> {
                put("type", "integer")
                put("value", value.toString())
            }
            is Long -> {
                put("type", "integer")
                put("value", value.toString())
            }
            is Float -> {
                put("type", "float")
                put("value", value.toDouble())
            }
            is Double -> {
                put("type", "float")
                put("value", value)
            }
            else -> {
                put("type", "text")
                put("value", value.toString())
            }
        }
    }

    private fun JsonElement.asString(): String? {
        val obj = jsonObject
        if (obj["type"]?.jsonPrimitive?.content == "null") return null
        return obj["value"]?.jsonPrimitive?.content
    }

    private fun JsonElement.asLong(): Long {
        val obj = jsonObject
        val raw = obj["value"]?.jsonPrimitive?.content
            ?: error("Atteso valore non null in $obj")
        return raw.toLong()
    }

    private fun JsonElement.asDouble(): Double {
        val obj = jsonObject
        val v = obj["value"] ?: error("Atteso valore non null in $obj")
        return v.jsonPrimitive.content.toDouble()
    }

    /**
     * Giorni tra [a] e [b] (date ISO `YYYY-MM-DD`), o null se una delle due manca o non e'
     * parsabile. Difensivo: date assenti/malformate -> null -> impianto trattato come ATTIVO.
     */
    private fun daysBetween(a: String?, b: String?): Int? {
        if (a == null || b == null) return null
        return try {
            ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }
}
