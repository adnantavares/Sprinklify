package com.challenge.sprinklify

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class PowerApiResponse(
    val properties: Properties
)

data class Properties(
    val parameter: Parameter
)

data class Parameter(
    @SerializedName("T2M")
    val temperature: Map<String, Double>,
    @SerializedName("PRECTOTCORR")
    val precipitation: Map<String, Double>,
    @SerializedName("WS10M")
    val windSpeed: Map<String, Double>,
    @SerializedName("SNODP")
    val snowDepth: Map<String, Double>
)

interface NasaApiService {
    @GET("temporal/daily/point")
    suspend fun getPowerData(
        @Query("parameters") parameters: String,
        @Query("community") community: String = "RE",
        @Query("longitude") longitude: String,
        @Query("latitude") latitude: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("format") format: String = "JSON"
    ): PowerApiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://power.larc.nasa.gov/api/"

    val instance: NasaApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(NasaApiService::class.java)
    }
}
