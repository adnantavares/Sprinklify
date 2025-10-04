package com.challenge.sprinklify

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.net.URLEncoder
import java.util.Calendar

@Composable
fun LocationPickerFragment(navController: NavController) {
    val context = LocalContext.current
    val fragmentManager = (context as FragmentActivity).supportFragmentManager
    val mapFragment = remember { SupportMapFragment.newInstance() }
    var selectedDate by remember { mutableStateOf("Select Date") }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                val frameLayout = android.widget.FrameLayout(it)
                frameLayout.id = android.view.View.generateViewId()
                fragmentManager
                    .beginTransaction()
                    .add(frameLayout.id, mapFragment)
                    .commit()
                frameLayout
            },
            update = {
                mapFragment.getMapAsync { googleMap ->
                    googleMap.uiSettings.isZoomGesturesEnabled = true

                    val initialLocation = LatLng(-34.0, 151.0)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 10f))

                    var currentMarker: Marker? = null

                    googleMap.setOnMapClickListener { latLng ->
                        selectedLocation = latLng
                        currentMarker?.remove()
                        currentMarker = googleMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Selected Location")
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                context,
                { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                    selectedDate = "${month + 1}/$dayOfMonth/$year"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            Button(onClick = { datePickerDialog.show() }) {
                Text(text = selectedDate)
            }

            Button(
                onClick = {
                    val encodedDate = URLEncoder.encode(selectedDate, "UTF-8")
                    navController.navigate("forecast/$encodedDate/${selectedLocation!!.latitude}/${selectedLocation!!.longitude}")
                },
                enabled = selectedDate != "Select Date" && selectedLocation != null
            ) {
                Text(text = "Will it rain on my parade?")
            }
        }
    }
}