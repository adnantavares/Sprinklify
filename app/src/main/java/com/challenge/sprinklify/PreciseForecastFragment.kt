package com.challenge.sprinklify

import android.util.Log
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Thermostat
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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreciseForecastFragment(navController: NavController, date: String, lat: String, lng: String) {
    var gesDiscHistory by remember { mutableStateOf<List<GesDiscParsedData>?>(null) }
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

                val latDouble = lat.toDouble()
                val lonDouble = lng.toDouble()

                val historicalData = mutableListOf<GesDiscParsedData>()
                for (year in startYear..endYear) {
                    try {
                        val queryDate = LocalDate.of(year, month, day)
                        val dailyData = GesDiscRepository.fetchAndParseSingleDayData(queryDate, latDouble, lonDouble)
                        historicalData.add(dailyData)
                    } catch (e: Exception) {
                        Log.e("GesDiscYearError", "Failed to fetch GES DISC data for $year", e)
                    }
                }
                gesDiscHistory = historicalData
            } catch (e: Exception) {
                Log.e("GesDiscApiError", "Error fetching GES DISC data", e)
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
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Selected Date: $date", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Lat: $lat, Lng: $lng", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))

                    val avgMorningTemp = gesDiscHistory?.mapNotNull { it.morningTemperatureInCelsius }?.average()
                    val avgAfternoonTemp = gesDiscHistory?.mapNotNull { it.afternoonTemperatureInCelsius }?.average()
                    val avgNightTemp = gesDiscHistory?.mapNotNull { it.nightTemperatureInCelsius }?.average()

                    val avgMorningWind = gesDiscHistory?.mapNotNull { it.morningWindSpeed }?.average()
                    val avgAfternoonWind = gesDiscHistory?.mapNotNull { it.afternoonWindSpeed }?.average()
                    val avgNightWind = gesDiscHistory?.mapNotNull { it.nightWindSpeed }?.average()

                    TimeOfDayWeatherCard(title = "Morning", temp = avgMorningTemp, wind = avgMorningWind)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimeOfDayWeatherCard(title = "Afternoon", temp = avgAfternoonTemp, wind = avgAfternoonWind)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimeOfDayWeatherCard(title = "Night", temp = avgNightTemp, wind = avgNightWind)
                }
            }
        }
    }
}

@Composable
fun TimeOfDayWeatherCard(title: String, temp: Double?, wind: Double?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                DetailItem(icon = Icons.Default.Thermostat, value = temp?.let { "%.2f°C".format(it) } ?: "N/A", label = "Avg Temp")
                DetailItem(icon = Icons.Default.Air, value = wind?.let { "%.2f m/s".format(it) } ?: "N/A", label = "Avg Wind")
            }
        }
    }
}

@Composable
fun DetailItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
