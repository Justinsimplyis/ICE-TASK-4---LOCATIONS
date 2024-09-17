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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var tvOutput: TextView
    private lateinit var searchBar: EditText
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchButton: Button

    private val locationPermissionCode = 1001
    private var currentLocation: Location? = null
    private var places = mutableListOf<Place>()

    private var searchJob: Job? = null // handles coroutine cancellation
    private var lastSearchTime = 0L // implements debounce
    private val searchCooldown = 500L // 500 ms debounce time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.lblOutput)
        searchBar = findViewById(R.id.searchBar)
        listView = findViewById(R.id.listView)
        progressBar = findViewById(R.id.progressBar)
        searchButton = findViewById(R.id.btnLocations)

        // initiates location and search
        searchButton.setOnClickListener {
            getLocation()
        }
    }

    private fun getLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        } else {
            // This rests the state of previous location and search before starting a new search
            currentLocation = null
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        }
    }//(DogsLocation. 2024)

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        tvOutput.text = "Your current location:\nLatitude: ${location.latitude}\nLongitude: ${location.longitude}"

        // this starts a new search only if the location is available
        searchNearbyPlaces()
    }

    private fun searchNearbyPlaces() {
        // Debounce the search to prevent rapid requests
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSearchTime < searchCooldown) {
            return
        }
        lastSearchTime = currentTime

        // This cancels any ongoing search
        searchJob?.cancel()

        val location = currentLocation ?: return
        val query = searchBar.text.toString()

        if (query.isBlank()) {
            Toast.makeText(this, "Please enter a search query.", Toast.LENGTH_SHORT).show()
            return
        }

        val categories = query.split(",").map { it.trim() }

        val client = OkHttpClient()
        //(OkHttp. 2022)

        places.clear()
        var completedRequests = 0
        val totalRequests = categories.size

        searchButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE

        Toast.makeText(this, "Searching nearby places...", Toast.LENGTH_SHORT).show()

        // This executes the search for each category
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            categories.forEach { category ->
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$category&lat=${location.latitude}&lon=${location.longitude}&radius=1000"

                val request = Request.Builder().url(url).build()

                try {
                    val response = client.newCall(request).execute()
                    val jsonResponse = response.body?.string() ?: return@launch

                    withContext(Dispatchers.Main) {
                        // Parses places from the search result for each category
                        val newPlaces = parsePlaces(jsonResponse)
                        places.addAll(newPlaces)
                        completedRequests++

                        // Checks if all requests are completed
                        if (completedRequests == totalRequests) {
                            updateSearchUI()
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to fetch data for $category", Toast.LENGTH_SHORT).show()
                        completedRequests++

                        // Ensure that list view is updated even if some requests fail
                        if (completedRequests == totalRequests) {
                            updateSearchUI()
                        }
                    }
                }
            }
        }//(nominatim. 2024), (Android Coroutine. 2020)
    }

    private fun updateSearchUI() {
        if (places.isEmpty()) {
            Toast.makeText(this, "No places found.", Toast.LENGTH_SHORT).show()
        } else {
            updateListView(places)
        }
        progressBar.visibility = ProgressBar.GONE
        searchButton.isEnabled = true
        Toast.makeText(this, "Search complete.", Toast.LENGTH_SHORT).show()
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
    }//(Android Developers, 2024)
}
//Reference List
//nominatim. 2024. Search queries. [Online] Available at: https://nominatim.org/release-docs/3.6/api/Search/. [Accessed on 13 September 2024]
//Android Developers. 2024. Request runtime permissions. [Online]. Available at: https://developer.android.com/training/permissions/requesting. [Accessed on 14 Sepetember 2024]
//Android Coroutines. 2020. Android Coroutines: How to manage async tasks in Kotlin. [Youtube] Available at: https://www.youtube.com/watch?v=6manrgTPzyA. [Accessed on 13 Sepetember 2024]
//OkHttp. 2022. OkHttp. [Online]. Available at: https://square.github.io/okhttp/. [Accessed on 15 September 2024]
//DogsLocation. 2024. DogsLocation . [nd]