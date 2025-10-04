package com.challenge.sprinklify

import android.util.Log
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastFragment(navController: NavController, date: String, lat: String, lng: String) {
    var avgTemp by remember { mutableStateOf<Double?>(null) }
    var avgPrecip by remember { mutableStateOf<Double?>(null) }
    var avgWind by remember { mutableStateOf<Double?>(null) }
    var avgSnow by remember { mutableStateOf<Double?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance()
                val endYear = calendar.get(Calendar.YEAR) - 1
                val startYear = endYear - 40

                val response = RetrofitClient.instance.getPowerData(
                    parameters = "T2M,PRECTOTCORR,WS10M,SNODP",
                    longitude = lng,
                    latitude = lat,
                    start = "$startYear",
                    end = "$endYear"
                )
                Log.d("NasaApiResponse", "Response: $response")

                val dateParts = date.split("/")
                val month = dateParts[0].toInt()
                val day = dateParts[1].toInt()
                val monthDayString = "%02d%02d".format(month, day)

                avgTemp = response.properties.parameter.temperature
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .values
                    .average()

                avgPrecip = response.properties.parameter.precipitation
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .values
                    .average()

                avgWind = response.properties.parameter.windSpeed
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .values
                    .average()

                avgSnow = response.properties.parameter.snowDepth
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .values
                    .average()

            } catch (e: Exception) {
                Log.e("NasaApiError", "Error fetching data", e)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rain Prediction") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                WeatherInfoCard(title = "Temperature", value = avgTemp?.let { "%.2f°C".format(it) } ?: "N/A", icon = Icons.Filled.Thermostat)
                Spacer(modifier = Modifier.height(16.dp))
                WeatherInfoCard(title = "Rain", value = avgPrecip?.let { "%.2f mm".format(it) } ?: "N/A", icon = Icons.Filled.WaterDrop)
                Spacer(modifier = Modifier.height(16.dp))
                WeatherInfoCard(title = "Wind", value = avgWind?.let { "%.2f m/s".format(it) } ?: "N/A", icon = Icons.Filled.Air)
                Spacer(modifier = Modifier.height(16.dp))
                WeatherInfoCard(title = "Snow", value = avgSnow?.let { "%.2f cm".format(it) } ?: "N/A", icon = Icons.Filled.AcUnit)
            }
        }
    }
}

@Composable
fun WeatherInfoCard(title: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}