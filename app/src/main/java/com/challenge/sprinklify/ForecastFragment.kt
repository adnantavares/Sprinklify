package com.challenge.sprinklify

import android.util.Log
import androidx.compose.foundation.clickable
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastFragment(navController: NavController, date: String, lat: String, lng: String) {
    var tempHistory by remember { mutableStateOf<Map<Int, Float>?>(null) }
    var precipHistory by remember { mutableStateOf<Map<Int, Float>?>(null) }
    var windHistory by remember { mutableStateOf<Map<Int, Float>?>(null) }
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

                val response = RetrofitClient.instance.getPowerData(
                    parameters = "T2M,PRECTOTCORR,WS10M,SNODP",
                    longitude = lng,
                    latitude = lat,
                    start = "$startYear",
                    end = "$endYear"
                )
                val monthDayString = "%02d%02d".format(month, day)

                tempHistory = response.properties.parameter.temperature
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .map { it.key.substring(0, 4).toInt() to it.value.toFloat() }
                    .toMap()
                precipHistory = response.properties.parameter.precipitation
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .map { it.key.substring(0, 4).toInt() to it.value.toFloat() }
                    .toMap()
                windHistory = response.properties.parameter.windSpeed
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .map { it.key.substring(0, 4).toInt() to it.value.toFloat() }
                    .toMap()
                snowHistory = response.properties.parameter.snowDepth
                    .filter { it.key.endsWith(monthDayString) && it.value != -999.0 }
                    .map { it.key.substring(0, 4).toInt() to it.value.toFloat() }
                    .toMap()
            } catch (e: Exception) {
                Log.e("NasaApiError", "Error fetching POWER data", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun navigateToDetails(title: String, data: Map<Int, Float>?) {
        data?.let {
            val json = Gson().toJson(it.values.toFloatArray())
            val encodedData = URLEncoder.encode(json, "UTF-8")
            navController.navigate("details/$title/$encodedData")
        }
    }

    CartoonTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Simple Forecast") },
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
                    WeatherInfoCard(
                        title = "Temperature",
                        value = tempHistory?.values?.let { "%.2f°C".format(it.average()) } ?: "N/A",
                        icon = Icons.Filled.Thermostat
                    ) {
                        navigateToDetails("Temperature", tempHistory)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    WeatherInfoCard(
                        title = "Rain",
                        value = precipHistory?.values?.let { "%.2f mm".format(it.average()) } ?: "N/A",
                        icon = Icons.Filled.WaterDrop
                    ) {
                        navigateToDetails("Rain", precipHistory)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    WeatherInfoCard(
                        title = "Wind",
                        value = windHistory?.values?.let { "%.2f m/s".format(it.average()) } ?: "N/A",
                        icon = Icons.Filled.Air
                    ) {
                        navigateToDetails("Wind", windHistory)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    WeatherInfoCard(
                        title = "Snow",
                        value = snowHistory?.values?.let { "%.2f cm".format(it.average()) } ?: "N/A",
                        icon = Icons.Filled.AcUnit
                    ) {
                        navigateToDetails("Snow", snowHistory)
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherInfoCard(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
