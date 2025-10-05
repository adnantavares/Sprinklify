package com.challenge.sprinklify

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsFragment(navController: NavController, title: String, data: FloatArray) {

    val timeSeriesEntries = remember(data) {
        data.mapIndexed { index, value ->
            val year = (2023 - data.size + 1) + index
            Entry(year.toFloat(), value)
        }
    }

    val (frequencyEntries, xAxisFormatter) = remember(data) {
        if (data.isEmpty()) {
            return@remember Pair(emptyList<BarEntry>(), object : ValueFormatter() {})
        }
        val frequencyMap = data.groupBy { (it * 2).toInt() / 2f }.mapValues { it.value.size }
        val sortedKeys = frequencyMap.keys.sorted()

        val entries = sortedKeys.mapIndexed { index, key ->
            BarEntry(index.toFloat(), frequencyMap[key]!!.toFloat())
        }

        val formatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                return sortedKeys.getOrNull(value.toInt())?.toString() ?: ""
            }
        }
        Pair(entries, formatter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Time Series (Last 40 Years)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                factory = { context ->
                    LineChart(context).apply {
                        val lineDataSet = LineDataSet(timeSeriesEntries, "$title Data").apply {
                            color = Color.BLUE
                            valueTextColor = Color.BLACK
                        }
                        this.data = LineData(lineDataSet)
                        this.xAxis.position = XAxis.XAxisPosition.BOTTOM
                        invalidate()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Frequency Distribution", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                factory = { context ->
                    BarChart(context).apply {
                        val barDataSet = BarDataSet(frequencyEntries, "$title Frequency").apply {
                            color = Color.CYAN
                            valueTextColor = Color.BLACK
                        }
                        this.data = BarData(barDataSet)
                        this.xAxis.position = XAxis.XAxisPosition.BOTTOM
                        this.xAxis.valueFormatter = xAxisFormatter
                        this.xAxis.setGranularity(1f)
                        invalidate()
                    }
                }
            )
        }
    }
}
