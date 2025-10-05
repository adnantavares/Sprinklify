package com.challenge.sprinklify

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "locationPicker") {
                composable("locationPicker") {
                    LocationPickerFragment(navController)
                }
                composable(
                    "forecast/{date}/{lat}/{lng}",
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType },
                        navArgument("lat") { type = NavType.StringType },
                        navArgument("lng") { type = NavType.StringType },
                    )
                ) { backStackEntry ->
                    ForecastFragment(
                        navController = navController,
                        date = backStackEntry.arguments?.getString("date") ?: "",
                        lat = backStackEntry.arguments?.getString("lat") ?: "",
                        lng = backStackEntry.arguments?.getString("lng") ?: ""
                    )
                }
                composable(
                    "details/{title}/{data}",
                    arguments = listOf(
                        navArgument("title") { type = NavType.StringType },
                        navArgument("data") { type = NavType.StringType },
                    )
                ) { backStackEntry ->
                    DetailsFragment(
                        navController = navController,
                        title = backStackEntry.arguments?.getString("title") ?: "",
                        data = Gson().fromJson(backStackEntry.arguments?.getString("data"), FloatArray::class.java)
                    )
                }
            }
        }
    }
}