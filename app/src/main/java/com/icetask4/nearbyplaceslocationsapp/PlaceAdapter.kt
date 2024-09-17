package com.icetask4.nearbyplaceslocationsapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView


data class Place(val name: String, val latitude: Double, val longitude: Double)

class PlaceAdapter(private val context: Context, private val places: MutableList<Place>) : BaseAdapter() {

    override fun getCount(): Int = places.size

    override fun getItem(position: Int): Any = places[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.place_list_item, parent, false)
        val place = places[position]

        val placeName = view.findViewById<TextView>(R.id.placeName)
        val placeCoordinates = view.findViewById<TextView>(R.id.placeCoordinates)

        placeName.text = place.name
        placeCoordinates.text = "Lat: ${place.latitude}, Lon: ${place.longitude}"

        return view
    }
}