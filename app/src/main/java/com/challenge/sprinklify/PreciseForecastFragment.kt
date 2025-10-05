package com.challenge.sprinklify

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.challenge.sprinklify.ui.theme.CartoonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreciseForecastFragment(navController: NavController, date: String, lat: String, lng: String) {
    var gesDiscHistory by remember { mutableStateOf<List<GesDiscParsedData>?>(null) }
    var snowHistory by remember { mutableStateOf<Map<Int, Float>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val dateParts = date.split("/")
                val month = dateParts[0].toInt()
                val day = dateParts[1].toInt()
                val calendar = Calendar.getInstance()
                val endYear = calendar.get(Calendar.YEAR) - 1
                val startYear = endYear - 40
                val totalYears = (endYear - startYear + 1).toFloat()
                val monthDayString = "%02d%02d".format(month, day)

                coroutineScope {
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

                    val historicalData = mutableListOf<GesDiscParsedData>()
                    (startYear..endYear).forEachIndexed { index, year ->
                        withContext(Dispatchers.Main) {
                            progress = (index + 1) / totalYears
                        }
                        try {
                            val queryDate = LocalDate.of(year, month, day)
                            val dailyData = GesDiscRepository.fetchAndParseSingleDayData(queryDate, lat.toDouble(), lng.toDouble())
                            historicalData.add(dailyData)
                        } catch (e: Exception) {
                            Log.e("GesDiscYearError", "Failed to fetch GES DISC data for $year", e)
                        }
                    }
                    gesDiscHistory = historicalData
                    snowHistory = snowDeferred.await()
                }
            } catch (e: Exception) {
                Log.e("PreciseForecastError", "Error fetching combined forecast data", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
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
                    actions = {
                        if (!isLoading) {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val csvData = createPreciseCsvData(gesDiscHistory, snowHistory)
                                    if (csvData.isNotEmpty()) {
                                        val fileName = "precise_forecast_${date.replace('/', '-')}.csv"
                                        saveCsvFile(context, fileName, csvData)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download CSV")
                            }
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
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Please wait while we gather detailed hourly data for the most accurate results.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
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

                    val hourlyTempAvgs = (0..23).map { hour ->
                        gesDiscHistory?.mapNotNull { it.hourlyTemperatures.getOrNull(hour) }?.average()?.takeIf { !it.isNaN() }
                    }
                    val hourlyWindAvgs = (0..23).map { hour ->
                        gesDiscHistory?.mapNotNull { it.hourlyWindSpeeds.getOrNull(hour) }?.average()?.takeIf { !it.isNaN() }
                    }
                    val hourlyPrecipAvgs = (0..23).map { hour ->
                        gesDiscHistory?.mapNotNull { it.hourlyPrecipitation.getOrNull(hour) }?.average()?.takeIf { !it.isNaN() }
                    }
                    val avgSnow = snowHistory?.values?.average()?.takeIf { !it.isNaN() }

                    item {
                        HourlyWeatherCard(
                            title = "Temperature",
                            icon = Icons.Default.Thermostat,
                            hourlyData = hourlyTempAvgs,
                            unit = "°C",
                            onHourClick = { hour ->
                                val title = "Temperature at ${ String.format("%02d:00", hour)}"
                                val data = gesDiscHistory?.mapNotNull { it.hourlyTemperatures.getOrNull(hour) }?.joinToString(",") { "%.2f".format(it) } ?: ""
                                if (data.isNotEmpty()) {
                                    navController.navigate("details/$title/$data")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HourlyWeatherCard(
                            title = "Wind Speed",
                            icon = Icons.Default.Air,
                            hourlyData = hourlyWindAvgs,
                            unit = "m/s",
                            onHourClick = { hour ->
                                val title = "Wind Speed at ${ String.format("%02d:00", hour)}"
                                val data = gesDiscHistory?.mapNotNull { it.hourlyWindSpeeds.getOrNull(hour) }?.joinToString(",") { "%.2f".format(it) } ?: ""
                                if (data.isNotEmpty()) {
                                    navController.navigate("details/$title/$data")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HourlyWeatherCard(
                            title = "Precipitation",
                            icon = Icons.Default.WaterDrop,
                            hourlyData = hourlyPrecipAvgs,
                            unit = "mm",
                            onHourClick = { hour ->
                                val title = "Precipitation at ${ String.format("%02d:00", hour)}"
                                val data = gesDiscHistory?.mapNotNull { it.hourlyPrecipitation.getOrNull(hour) }?.joinToString(",") { "%.2f".format(it) } ?: ""
                                if (data.isNotEmpty()) {
                                    navController.navigate("details/$title/$data")
                                }
                            }
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
fun HourlyWeatherCard(
    title: String,
    icon: ImageVector,
    hourlyData: List<Double?>,
    unit: String,
    onHourClick: (hour: Int) -> Unit
) {
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
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                (0..23).forEach { hour ->
                    HourlyDataItem(
                        hour = hour,
                        value = hourlyData.getOrNull(hour),
                        unit = unit,
                        onClick = { onHourClick(hour) }
                    )
                }
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
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
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
fun HourlyDataItem(hour: Int, value: Double?, unit: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%02d:00".format(hour),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value?.let { "%.2f $unit".format(it) } ?: "N/A",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun createPreciseCsvData(
    gesDiscHistory: List<GesDiscParsedData>?,
    snowHistory: Map<Int, Float>?
): String {
    if (gesDiscHistory.isNullOrEmpty()) return ""

    val header = "year,month,day,hour,temperature_celsius,wind_speed_m_s,precipitation_mm,snow_depth_cm\n"
    val csvBuilder = StringBuilder(header)

    gesDiscHistory.sortedBy { it.date.year }.forEach { dailyData ->
        val year = dailyData.date.year
        val month = dailyData.date.monthValue
        val day = dailyData.date.dayOfMonth
        val snowDepth = snowHistory?.get(year) ?: ""

        (0..23).forEach { hour ->
            val temp = dailyData.hourlyTemperatures.getOrNull(hour) ?: ""
            val wind = dailyData.hourlyWindSpeeds.getOrNull(hour) ?: ""
            val precip = dailyData.hourlyPrecipitation.getOrNull(hour) ?: ""
            csvBuilder.append("$year,$month,$day,$hour,$temp,$wind,$precip,$snowDepth\n")
        }
    }
    return csvBuilder.toString()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun saveCsvFile(context: Context, fileName: String, content: String) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }

    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        try {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("SaveCsvError", "Error saving CSV file", e)
        }
    }
}
