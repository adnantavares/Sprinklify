package com.challenge.sprinklify

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.challenge.sprinklify.ui.theme.CartoonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreciseForecastFragment(navController: NavController, date: String, lat: String, lng: String) {
    var gesDiscHistory by remember { mutableStateOf<List<GesDiscParsedData>?>(null) }
    var snowHistory by remember { mutableStateOf<Map<Int, Float>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val dateParts = date.split("/")
                val month = dateParts[0].toInt()
                val day = dateParts[1].toInt()
                val calendar = Calendar.getInstance()
                val endYear = calendar.get(Calendar.YEAR) - 1
                val startYear = endYear - 40
                val monthDayString = "%02d%02d".format(month, day)

                coroutineScope {
                    val gesDiscDeferred = async {
                        val historicalData = mutableListOf<GesDiscParsedData>()
                        for (year in startYear..endYear) {
                            try {
                                val queryDate = LocalDate.of(year, month, day)
                                val dailyData = GesDiscRepository.fetchAndParseSingleDayData(queryDate, lat.toDouble(), lng.toDouble())
                                historicalData.add(dailyData)
                            } catch (e: Exception) {
                                Log.e("GesDiscYearError", "Failed to fetch GES DISC data for $year", e)
                            }
                        }
                        historicalData
                    }

                    val snowDeferred = async {
                        try {
                            RetrofitClient.instance.getPowerData(
                                parameters = "SNODP",
                                longitude = lng,
                                latitude = lat,
                                start = "$startYear",
                                end = "$endYear"
                            ).properties.parameter.snowDepth
                                .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                                .map { it.key.substring(0, 4).toInt() to it.value.toFloat() }
                                .toMap()
                        } catch (e: Exception) {
                            Log.e("PowerApiSnowError", "Error fetching snow data from POWER API", e)
                            emptyMap<Int, Float>()
                        }
                    }

                    gesDiscHistory = gesDiscDeferred.await()
                    snowHistory = snowDeferred.await()
                }
            } catch (e: Exception) {
                Log.e("PreciseForecastError", "Error fetching combined forecast data", e)
            } finally {
                isLoading = false
            }
        }
    }

    CartoonTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Precision Forecast") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { innerPadding ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(text = "Selected Date: $date", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Lat: $lat, Lng: $lng", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    val avgMorningTemp = gesDiscHistory?.mapNotNull { it.morningTemperatureInCelsius }?.average()?.takeIf { !it.isNaN() }
                    val avgAfternoonTemp = gesDiscHistory?.mapNotNull { it.afternoonTemperatureInCelsius }?.average()?.takeIf { !it.isNaN() }
                    val avgNightTemp = gesDiscHistory?.mapNotNull { it.nightTemperatureInCelsius }?.average()?.takeIf { !it.isNaN() }

                    val avgMorningWind = gesDiscHistory?.mapNotNull { it.morningWindSpeed }?.average()?.takeIf { !it.isNaN() }
                    val avgAfternoonWind = gesDiscHistory?.mapNotNull { it.afternoonWindSpeed }?.average()?.takeIf { !it.isNaN() }
                    val avgNightWind = gesDiscHistory?.mapNotNull { it.nightWindSpeed }?.average()?.takeIf { !it.isNaN() }

                    val avgMorningPrecip = gesDiscHistory?.mapNotNull { it.morningPrecipitationInMm }?.average()?.takeIf { !it.isNaN() }
                    val avgAfternoonPrecip = gesDiscHistory?.mapNotNull { it.afternoonPrecipitationInMm }?.average()?.takeIf { !it.isNaN() }
                    val avgNightPrecip = gesDiscHistory?.mapNotNull { it.nightPrecipitationInMm }?.average()?.takeIf { !it.isNaN() }

                    val avgSnow = snowHistory?.values?.average()?.takeIf { !it.isNaN() }

                    item {
                        WeatherMetricCard(
                            title = "Temperature",
                            icon = Icons.Default.Thermostat,
                            morningValue = avgMorningTemp?.let { "%.2f°C".format(it) } ?: "N/A",
                            afternoonValue = avgAfternoonTemp?.let { "%.2f°C".format(it) } ?: "N/A",
                            nightValue = avgNightTemp?.let { "%.2f°C".format(it) } ?: "N/A"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WeatherMetricCard(
                            title = "Wind Speed",
                            icon = Icons.Default.Air,
                            morningValue = avgMorningWind?.let { "%.2f m/s".format(it) } ?: "N/A",
                            afternoonValue = avgAfternoonWind?.let { "%.2f m/s".format(it) } ?: "N/A",
                            nightValue = avgNightWind?.let { "%.2f m/s".format(it) } ?: "N/A"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WeatherMetricCard(
                            title = "Precipitation",
                            icon = Icons.Default.WaterDrop,
                            morningValue = avgMorningPrecip?.let { "%.2f mm".format(it) } ?: "N/A",
                            afternoonValue = avgAfternoonPrecip?.let { "%.2f mm".format(it) } ?: "N/A",
                            nightValue = avgNightPrecip?.let { "%.2f mm".format(it) } ?: "N/A"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DailyAverageCard(
                            title = "Snow Depth",
                            icon = Icons.Default.AcUnit,
                            value = avgSnow?.let { "%.2f cm".format(it) } ?: "N/A"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherMetricCard(title: String, icon: ImageVector, morningValue: String, afternoonValue: String, nightValue: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                TimeOfDayItem(icon = Icons.Default.WbSunny, label = "Morning", value = morningValue)
                TimeOfDayItem(icon = Icons.Default.WbSunny, label = "Afternoon", value = afternoonValue) // Consider a different icon for afternoon
                TimeOfDayItem(icon = Icons.Default.NightsStay, label = "Night", value = nightValue)
            }
        }
    }
}

@Composable
fun DailyAverageCard(title: String, icon: ImageVector, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun TimeOfDayItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
