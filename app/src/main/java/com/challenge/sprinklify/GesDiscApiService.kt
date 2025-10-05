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

data class GesDiscParsedData(
    val date: LocalDate,
    val morningTemperatureInCelsius: Double?,
    val afternoonTemperatureInCelsius: Double?,
    val nightTemperatureInCelsius: Double?,
    val morningWindSpeed: Double?,
    val afternoonWindSpeed: Double?,
    val nightWindSpeed: Double?,
    val morningPrecipitationInMm: Double?,
    val afternoonPrecipitationInMm: Double?,
    val nightPrecipitationInMm: Double?
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

        fun getAverages(data: List<Double>?): Map<String, Double> {
            if (data == null || data.size != 25) return emptyMap()
            return mapOf(
                "morning" to data.subList(0, 8).average(),
                "afternoon" to data.subList(8, 16).average(),
                "night" to data.subList(16, 24).average()
            )
        }

        val tempAvgs = getAverages(slvData["T2M"])
        val uWindAvgs = getAverages(slvData["U10M"])
        val vWindAvgs = getAverages(slvData["V10M"])
        val precipAvgs = getAverages(flxData["PRECTOT"])

        val morningU = uWindAvgs["morning"]
        val morningV = vWindAvgs["morning"]
        val morningWind = if (morningU != null && morningV != null) sqrt(morningU.pow(2) + morningV.pow(2)) else null

        val afternoonU = uWindAvgs["afternoon"]
        val afternoonV = vWindAvgs["afternoon"]
        val afternoonWind = if (afternoonU != null && afternoonV != null) sqrt(afternoonU.pow(2) + afternoonV.pow(2)) else null

        val nightU = uWindAvgs["night"]
        val nightV = vWindAvgs["night"]
        val nightWind = if (nightU != null && nightV != null) sqrt(nightU.pow(2) + nightV.pow(2)) else null

        // Rate is in kg/m^2/s (or mm/s). To get total precipitation for a period,
        // multiply the average rate by the period duration in seconds (8 hours).
        val precipConversionFactor = 8 * 3600

        GesDiscParsedData(
            date = date,
            morningTemperatureInCelsius = tempAvgs["morning"]?.minus(273.15),
            afternoonTemperatureInCelsius = tempAvgs["afternoon"]?.minus(273.15),
            nightTemperatureInCelsius = tempAvgs["night"]?.minus(273.15),
            morningWindSpeed = morningWind,
            afternoonWindSpeed = afternoonWind,
            nightWindSpeed = nightWind,
            morningPrecipitationInMm = precipAvgs["morning"]?.times(precipConversionFactor),
            afternoonPrecipitationInMm = precipAvgs["afternoon"]?.times(precipConversionFactor),
            nightPrecipitationInMm = precipAvgs["night"]?.times(precipConversionFactor)
        )
    }
}
