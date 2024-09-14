package com.icetask4.nearbyplaceslocationsapp


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException


class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var tvOutput: TextView
    private lateinit var searchBar: EditText
    private lateinit var listView: ListView
    private val locationPermissionCode = 1001
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.lblOutput)
        searchBar = findViewById(R.id.searchBar)
        listView = findViewById(R.id.listView)


        findViewById<Button>(R.id.btnLocations).setOnClickListener {
            getLocation()
        }
    }

    private fun getLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        tvOutput.text = "Your current location:\nLatitude: ${location.latitude}\nLongitude: ${location.longitude}"
        searchNearbyPlaces()
    }

    private fun searchNearbyPlaces() {
        val location = currentLocation ?: return
        val query = searchBar.text.toString()

        // Split query by comma and trim spaces
        val categories = query.split(",").map { it.trim() }

        val client = OkHttpClient()
        val places = mutableListOf<Place>()
        val totalRequests = categories.size
        var completedRequests = 0

        categories.forEach { category ->
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$category&lat=${location.latitude}&lon=${location.longitude}&radius=1000"

            val request = Request.Builder().url(url).build()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = client.newCall(request).execute()
                    val jsonResponse = response.body?.string() ?: return@launch
                    withContext(Dispatchers.Main) {
                        // Append places from the search result for each category
                        val newPlaces = parsePlaces(jsonResponse)
                        places.addAll(newPlaces)
                        completedRequests++

                        // Check if all requests are completed
                        if (completedRequests == totalRequests) {
                            updateListView(places)
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to fetch data for $category", Toast.LENGTH_SHORT).show()
                        completedRequests++

                        // Check if all requests are completed even if some failed
                        if (completedRequests == totalRequests) {
                            updateListView(places)
                        }
                    }
                }
            }
        }
    }

    private fun parsePlaces(jsonResponse: String): List<Place> {
        val jsonArray = JSONArray(jsonResponse)
        val places = mutableListOf<Place>()

        if (jsonArray.length() == 0) {
            return places
        }

        for (i in 0 until jsonArray.length()) {
            val place = jsonArray.getJSONObject(i)
            val name = place.optString("display_name", "Unnamed Place")
            val lat = place.getDouble("lat")
            val lon = place.getDouble("lon")
            places.add(Place(name, lat, lon))
        }

        return places
    }
    private fun updateListView(places: List<Place>) {
        val adapter = PlaceAdapter(this, places.toMutableList())
        listView.adapter = adapter
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }
}