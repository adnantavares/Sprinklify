package com.challenge.sprinklify

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "location") {
                composable("location") {
                    LocationPickerFragment(navController)
                }
                composable("forecast/{date}/{lat}/{lng}") { backStackEntry ->
                    val date = backStackEntry.arguments?.getString("date") ?: ""
                    val lat = backStackEntry.arguments?.getString("lat") ?: ""
                    val lng = backStackEntry.arguments?.getString("lng") ?: ""
                    ForecastFragment(navController, date, lat, lng)
                }
                composable("precise-forecast/{date}/{lat}/{lng}") { backStackEntry ->
                    val date = backStackEntry.arguments?.getString("date") ?: ""
                    val lat = backStackEntry.arguments?.getString("lat") ?: ""
                    val lng = backStackEntry.arguments?.getString("lng") ?: ""
                    PreciseForecastFragment(navController, date, lat, lng)
                }
                composable("details/{title}/{data}") { backStackEntry ->
                    val title = backStackEntry.arguments?.getString("title") ?: ""
                    val data = backStackEntry.arguments?.getString("data") ?: ""
                    val floatArray = data.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
                    DetailsFragment(navController, title, floatArray)
                }
            }
        }
    }
}
