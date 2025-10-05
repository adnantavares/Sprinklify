package com.challenge.sprinklify

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.challenge.sprinklify.ui.theme.CartoonTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.net.URLEncoder
import java.util.Calendar
import com.challenge.sprinklify.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerFragment(navController: NavController) {
    val context = LocalContext.current
    val fragmentManager = (context as FragmentActivity).supportFragmentManager
    val mapFragment = remember { SupportMapFragment.newInstance() }
    var selectedDate by remember { mutableStateOf("Select Date") }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }

    CartoonTheme {
        Scaffold {
            innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sprinklify_transparency),
                        contentDescription = "App Logo",
                        modifier = Modifier.height(60.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val calendar = Calendar.getInstance()
                    val datePickerDialog = DatePickerDialog(
                        context,
                        { _: DatePicker, _: Int, month: Int, dayOfMonth: Int ->
                            selectedDate = "${month + 1}/$dayOfMonth"
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    Button(
                        onClick = { datePickerDialog.show() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = if (selectedDate == "Select Date") selectedDate else "Date: $selectedDate")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val encodedDate = URLEncoder.encode(selectedDate, "UTF-8")
                            navController.navigate("forecast/$encodedDate/${selectedLocation!!.latitude}/${selectedLocation!!.longitude}")
                        },
                        enabled = selectedDate != "Select Date" && selectedLocation != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text(text = "Will it rain on my parade?")
                    }
                }
            }
        }
    }
}
