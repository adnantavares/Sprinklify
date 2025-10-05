package com.challenge.sprinklify

import com.challenge.sprinklify.BuildConfig
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

/**
 * Interceptor to add the Authorization token to GES DISC requests.
 * The token is read from BuildConfig, which is populated from local.properties.
 */
private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = BuildConfig.GES_DISC_BEARER_TOKEN

        if (token.isBlank()) {
            // Proceed without the header if the token is missing.
            // Consider logging a warning here in a real application.
            return chain.proceed(chain.request())
        }

        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
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

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .build()

    val instance: GesDiscApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()
            .create(GesDiscApiService::class.java)
    }
}

// --- Query and Data Model ---

object GesDiscQueryBuilder {
    // Using the hourly single-level diagnostics dataset (SLV)
    private const val DATASET_URL_PREFIX = "opendap/MERRA2/M2T1NXSLV.5.12.4"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
    private val monthFormatter = DateTimeFormatter.ofPattern("MM")

    // Variables available in the M2T1NXSLV dataset.
    // Note: Precipitation (PRECTOT) and Snow (SNOMAS/SNODP) are in different MERRA-2 datasets.
    val merra2Variables = mapOf(
        "temperature" to "T2M", // 2-meter air temperature (Kelvin)
        "wind_u" to "U10M",      // 10-meter eastward wind (m s-1)
        "wind_v" to "V10M"       // 10-meter northward wind (m s-1)
    )

    fun buildUrl(date: LocalDate, lat: Double, lon: Double, variables: List<String>): String {
        val year = date.format(yearFormatter)
        val month = date.format(monthFormatter)
        val dayFilename = date.format(dateFormatter)

        val latIndex = ((lat + 90.0) / 0.5).toInt().coerceIn(0, 360)
        val lonIndex = ((lon + 180.0) / 0.625).toInt().coerceIn(0, 575)

        // Fetch all 24 hourly time steps for the day to calculate a daily average.
        val timeQuery = "[0:23]"

        val variableQuery = variables.joinToString(",") { variable ->
            "$variable$timeQuery[$latIndex:$latIndex][$lonIndex:$lonIndex]"
        }

        return "${GesDiscRetrofitClient.BASE_URL}$DATASET_URL_PREFIX/$year/$month/MERRA2_400.tavg1_2d_slv_Nx.${dayFilename}.nc4.ascii?$variableQuery"
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
    val precipitation: Double?, // Placeholder, not available in this specific dataset
    val snowMass: Double?      // Placeholder, not available in this specific dataset
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
                    requestedVariables.find { description.startsWith(it) }?.let {
                        results[it]?.add(value)
                    }
                }
            }
        }
        return results
    }
}

object GesDiscRepository {
    suspend fun fetchAndParseSingleDayData(date: LocalDate, lat: Double, lon: Double): GesDiscParsedData {
        val variablesToFetch = GesDiscQueryBuilder.merra2Variables.values.toList()
        val url = GesDiscQueryBuilder.buildUrl(date, lat, lon, variablesToFetch)

        val response = GesDiscRetrofitClient.instance.getOpenDapData(url)

        if (!response.isSuccessful || response.body() == null) {
            throw Exception("Failed to fetch data from GES DISC: ${response.errorBody()?.string()}")
        }

        val responseText = response.body()!!.string()
        val parsedMap = GesDiscDataParser.parse(responseText, variablesToFetch)

        // Helper to calculate averages for different time periods
        fun getAverages(data: List<Double>?): Map<String, Double> {
            if (data == null || data.size != 25) return emptyMap()
            return mapOf(
                // Morning: 06:00 - 11:59 (indices 6-11)
                "morning" to data.subList(6, 12).average(),
                // Afternoon: 12:00 - 17:59 (indices 12-17)
                "afternoon" to data.subList(12, 18).average(),
                // Night: 18:00 - 05:59 (indices 18-23 and 0-5)
                "night" to (data.subList(18, 24) + data.subList(0, 6)).average()
            )
        }

        val tempKelvinAvgs = getAverages(parsedMap[GesDiscQueryBuilder.merra2Variables["temperature"]])
        val morningTempC = tempKelvinAvgs["morning"]?.minus(273.15)
        val afternoonTempC = tempKelvinAvgs["afternoon"]?.minus(273.15)
        val nightTempC = tempKelvinAvgs["night"]?.minus(273.15)

        val uWindAvgs = getAverages(parsedMap[GesDiscQueryBuilder.merra2Variables["wind_u"]])
        val vWindAvgs = getAverages(parsedMap[GesDiscQueryBuilder.merra2Variables["wind_v"]])

        val morningU = uWindAvgs["morning"]
        val morningV = vWindAvgs["morning"]
        val morningWindSpeed = if (morningU != null && morningV != null) sqrt(morningU.pow(2) + morningV.pow(2)) else null

        val afternoonU = uWindAvgs["afternoon"]
        val afternoonV = vWindAvgs["afternoon"]
        val afternoonWindSpeed = if (afternoonU != null && afternoonV != null) sqrt(afternoonU.pow(2) + afternoonV.pow(2)) else null

        val nightU = uWindAvgs["night"]
        val nightV = vWindAvgs["night"]
        val nightWindSpeed = if (nightU != null && nightV != null) sqrt(nightU.pow(2) + nightV.pow(2)) else null

        return GesDiscParsedData(
            date = date,
            morningTemperatureInCelsius = morningTempC,
            afternoonTemperatureInCelsius = afternoonTempC,
            nightTemperatureInCelsius = nightTempC,
            morningWindSpeed = morningWindSpeed,
            afternoonWindSpeed = afternoonWindSpeed,
            nightWindSpeed = nightWindSpeed,
            precipitation = null,
            snowMass = null
        )
    }
}
