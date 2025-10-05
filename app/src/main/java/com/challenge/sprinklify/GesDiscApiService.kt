package com.challenge.sprinklify

import com.challenge.sprinklify.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service, builders, and parsers for fetching and processing data from NASA's GES DISC OPeNDAP service.
 */

// --- Network Layer ---

private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = BuildConfig.GES_DISC_BEARER_TOKEN
        if (token.isBlank()) return chain.proceed(chain.request())
        val newRequest = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(newRequest)
    }
}

interface GesDiscApiService {
    @GET
    suspend fun getOpenDapData(@Url url: String): Response<ResponseBody>
}

object GesDiscRetrofitClient {
    const val BASE_URL = "https://goldsmr4.gesdisc.eosdis.nasa.gov/"
    private val okHttpClient = OkHttpClient.Builder().addInterceptor(AuthInterceptor()).build()
    val instance: GesDiscApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient).build().create(GesDiscApiService::class.java)
    }
}

// --- Query and Data Model ---

object GesDiscQueryBuilder {
    private const val SLV_PREFIX = "opendap/MERRA2/M2T1NXSLV.5.12.4"
    private const val FLX_PREFIX = "opendap/MERRA2/M2T1NXFLX.5.12.4"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
    private val monthFormatter = DateTimeFormatter.ofPattern("MM")

    val slvVariables = listOf("T2M", "U10M", "V10M")
    val flxVariables = listOf("PRECTOT") // Only fetching precipitation

    private fun getStreamForYear(year: Int): Int {
        return when {
            year >= 2011 -> 400
            year >= 2001 -> 300
            year >= 1992 -> 200
            else -> 100 // For years 1980-1991
        }
    }

    fun buildUrl(dataset: String, date: LocalDate, lat: Double, lon: Double, variables: List<String>): String {
        val prefix = when (dataset) {
            "SLV" -> SLV_PREFIX
            "FLX" -> FLX_PREFIX
            else -> throw IllegalArgumentException("Unknown dataset: $dataset")
        }
        val fileId = dataset.lowercase()

        val year = date.format(yearFormatter)
        val month = date.format(monthFormatter)
        val dayFilename = date.format(dateFormatter)

        val latIndex = ((lat + 90.0) / 0.5).toInt().coerceIn(0, 360)
        val lonIndex = ((lon + 180.0) / 0.625).toInt().coerceIn(0, 575)
        val timeQuery = "[0:23]"

        val variableQuery = variables.joinToString(",") { "$it$timeQuery[$latIndex:$latIndex][$lonIndex:$lonIndex]" }
        val stream = getStreamForYear(date.year)

        return "${GesDiscRetrofitClient.BASE_URL}$prefix/$year/$month/MERRA2_${stream}.tavg1_2d_${fileId}_Nx.${dayFilename}.nc4.ascii?$variableQuery"
    }
}

// Holds the full 24-hour data for each metric

data class GesDiscParsedData(
    val date: LocalDate,
    val hourlyTemperatures: List<Double?>,
    val hourlyWindSpeeds: List<Double?>,
    val hourlyPrecipitation: List<Double?>
)

// --- Parsing and Repository Layer ---

object GesDiscDataParser {
    fun parse(responseText: String, requestedVariables: List<String>): Map<String, List<Double>> {
        val results = requestedVariables.associateWith { mutableListOf<Double>() }.toMutableMap()
        responseText.lines().forEach { line ->
            val parts = line.split(',')
            if (parts.size >= 2) {
                val description = parts[0]
                val value = parts.last().trim().toDoubleOrNull()
                if (value != null) {
                    requestedVariables.find { description.startsWith(it) }?.let { results[it]?.add(value) }
                }
            }
        }
        return results
    }
}

object GesDiscRepository {
    private suspend fun fetchForDataset(dataset: String, date: LocalDate, lat: Double, lon: Double, variables: List<String>): Map<String, List<Double>> {
        if (variables.isEmpty()) return emptyMap()
        val url = GesDiscQueryBuilder.buildUrl(dataset, date, lat, lon, variables)
        val response = GesDiscRetrofitClient.instance.getOpenDapData(url)
        if (!response.isSuccessful || response.body() == null) {
            throw Exception("Failed to fetch from $dataset for year ${date.year}: ${response.errorBody()?.string()}")
        }
        return GesDiscDataParser.parse(response.body()!!.string(), variables)
    }

    suspend fun fetchAndParseSingleDayData(date: LocalDate, lat: Double, lon: Double): GesDiscParsedData = coroutineScope {
        val slvDataDeferred = async { fetchForDataset("SLV", date, lat, lon, GesDiscQueryBuilder.slvVariables) }
        val flxDataDeferred = async { fetchForDataset("FLX", date, lat, lon, GesDiscQueryBuilder.flxVariables) }

        val slvData = slvDataDeferred.await()
        val flxData = flxDataDeferred.await()

        // Cleans data by removing fill values and applying a realistic range, then pads to ensure a size of 24.
        fun cleanAndPad(data: List<Double>?, validRange: ClosedRange<Double>? = null): List<Double?> {
            val fillValueThreshold = 1e10
            val cleaned = data?.map {
                val isValid = it != null && it < fillValueThreshold && (validRange == null || it in validRange)
                if (isValid) it else null
            } ?: emptyList()
            // Take the first 24 items, and if the list is shorter, pad with nulls to ensure a size of 24.
            return (cleaned + List(24) { null }).take(24)
        }

        // Define realistic ranges to filter out absurd, non-physical values.
        val tempRangeK = 183.0..333.0       // -90C to 60C
        val windRange = -150.0..150.0     // For U/V components
        val precipRateRange = 0.0..1.0    // Precipitation rate in kg/m^2/s (1.0 is ~3600 mm/hr)

        val hourlyTempsK = cleanAndPad(slvData["T2M"], tempRangeK)
        val uWind = cleanAndPad(slvData["U10M"], windRange)
        val vWind = cleanAndPad(slvData["V10M"], windRange)
        val precipRates = cleanAndPad(flxData["PRECTOT"], precipRateRange)

        val hourlyTempsC = hourlyTempsK.map { tempK -> tempK?.minus(273.15) }

        val hourlyWindSpeeds = uWind.zip(vWind).map { (u, v) ->
            if (u != null && v != null) sqrt(u.pow(2) + v.pow(2)) else null
        }

        // Rate is in kg/m^2/s (or mm/s). To get total mm for one hour, multiply by 3600.
        val hourlyPrecipitation = precipRates.map { rate -> rate?.times(3600) }

        GesDiscParsedData(
            date = date,
            hourlyTemperatures = hourlyTempsC,
            hourlyWindSpeeds = hourlyWindSpeeds,
            hourlyPrecipitation = hourlyPrecipitation
        )
    }
}
