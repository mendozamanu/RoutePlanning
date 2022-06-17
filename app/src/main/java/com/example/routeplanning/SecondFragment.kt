package com.example.routeplanning

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.routeplanning.BuildConfig.GOOGLE_MAPS_API
import com.example.routeplanning.databinding.FragmentSecondBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.io.IOException


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */


class SecondFragment: Fragment(), OnMapReadyCallback,
    GoogleMap.OnMyLocationButtonClickListener,
    GoogleMap.OnMarkerDragListener,
    GoogleMap.OnMyLocationClickListener{ //extends Fragment and implements callbacks and listeners

    private var _binding: FragmentSecondBinding? = null
    private lateinit var mMap: GoogleMap
    private var marker: Marker? = null
    private var marker2: Marker? = null
    private var address = ""
    private var address2 = ""
    private var result = arrayListOf("","")
    private var clicked = 0
    private var polyline = 0
    private var autocompleteFragment : AutocompleteSupportFragment? = null
    private var autocompleteFragment2 : AutocompleteSupportFragment? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view: View = inflater.inflate(R.layout.fragment_second, container, false)

        Places.initialize(requireActivity(), GOOGLE_MAPS_API)

        // Initialize the AutocompleteSupportFragment.
        autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment

        autocompleteFragment2 =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment2)
                    as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment!!.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,
            Place.Field.LAT_LNG))
        autocompleteFragment2!!.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,
            Place.Field.LAT_LNG))

        autocompleteFragment!!.setHint("Buscar")
        autocompleteFragment2!!.setHint("Buscar")

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment!!.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                marker?.remove()
                Log.i("TAG", "Place: ${place.name}, ${place.id}, ${place.latLng}")
                marker = mMap.addMarker(MarkerOptions().position(place.latLng!!)
                    .draggable(true))!!
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.latLng!!, 15.0F))
                address = place.name!!.toString()
            }

            override fun onError(status: Status) {
                Log.i("TAG", "An error occurred: $status")
            }
        })

        autocompleteFragment2!!.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                marker2?.remove()
                Log.i("TAG", "Place: ${place.name}, ${place.id}, ${place.latLng}")
                marker2 = mMap.addMarker(MarkerOptions().position(place.latLng!!)
                    .draggable(true))!!
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.latLng!!, 15.0F))
                address2 = place.name!!.toString()
            }

            override fun onError(status: Status) {
                // TODO: Handle the error.
                Log.i("TAG", "An error occurred: $status")
            }
        })

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setFragmentResultListener("requestedAddrO") { _, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            Log.d("RESULT", result.toString())
            address = result[0]
            address2 = result[1]
            if(result[0]!="") {
                autocompleteFragment!!.setText(result[0])
            }
            if(result[1]!="") {
                autocompleteFragment2!!.setText(result[1])
            }

        }//Origin requested
        setFragmentResultListener("requestedAddrD") { _, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            Log.d("RESULT", result.toString())
            address = result[0]
            address2 = result[1]
            if(result[0]!="") {
                autocompleteFragment!!.setText(result[0])
            }
            if(result[1]!="") {
                autocompleteFragment2!!.setText(result[1])
            }

        }//Destination requested

        val secondButton: FloatingActionButton = view.findViewById(R.id.buttonSecond)
        secondButton.setOnClickListener {
            result[0] = address
            result[1] = address2

            if(polyline == 1){ //Clear previous polyline
                mMap.clear()
                marker = mMap.addMarker(MarkerOptions().position(marker!!.position)
                    .draggable(true))
                marker2 = mMap.addMarker(MarkerOptions().position(marker2!!.position)
                    .draggable(true))
                polyline=0
            }
            setFragmentResult(
                    "directionsRequested",
                    bundleOf("bundleKey" to result)
            )
            //Route generation from the given points
            if(address != "" && address2 !=""){
                val path: MutableList<List<LatLng>> = ArrayList()
                val urlDirections = "https://maps.googleapis.com/maps/api/directions/json?origin="+
                        marker?.position?.latitude.toString()+","+marker?.position?.
                        longitude.toString()+"&destination="+
                        marker2?.position?.latitude.toString()+","+marker2?.position?.
                        longitude.toString()+"&key="+
                        GOOGLE_MAPS_API

                val builder = LatLngBounds.Builder()
                builder.include(marker!!.position)
                builder.include(marker2!!.position)
                val bounds = builder.build()
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                val padding = (width*0.3).toInt() // offset from edges of the map in pixels

                val directionsRequest = object : StringRequest(
                    Method.GET,
                    urlDirections, Response.Listener {
                        response ->
                    val jsonResponse = JSONObject(response)
                    // Get routes
                    val routes = jsonResponse.getJSONArray("routes")
                    val legs = routes.getJSONObject(0).getJSONArray("legs")
                    val steps = legs.getJSONObject(0).getJSONArray("steps")
                    for (i in 0 until steps.length()) {
                        val points = steps.getJSONObject(i).getJSONObject("polyline")
                            .getString("points")
                        path.add(PolyUtil.decode(points))
                    }
                    for (i in 0 until path.size) {
                        this.mMap.addPolyline(PolylineOptions().
                            addAll(path[i]).color(Color.BLUE))
                    }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,
                        width, height, padding))

                    polyline=1

                    }, Response.ErrorListener {
                }){}
                val requestQueue = Volley.newRequestQueue(requireContext())
                requestQueue.add(directionsRequest)
            }
            //findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)

        }
        return view

    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap){
        mMap = googleMap
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMarkerDragListener(this)

        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            Toast.makeText(activity, "Acepta los permisos de localización para " +
                    "poder guardar tu ubicación actual", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100)
            if (ContextCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
        }

        Log.d("Addr", address)
        if(address != "") {
            val geocoder = Geocoder(requireActivity())
            val builder = LatLngBounds.Builder()
            Log.d("RESULT", result.toString())

            try {
                if(result[0]!="") {
                    // May throw an IOException
                    marker?.remove()
                    val addr = geocoder.getFromLocationName(result[0], 1)
                    Log.d("ADDR", addr[0].toString())
                    val location: Address = addr[0]
                    val p1 = LatLng(location.latitude, location.longitude)
                    marker = mMap.addMarker(MarkerOptions().position(p1)
                        .draggable(true))!!
                    builder.include(marker!!.position)
                }
                if(result[1]!=""){
                    marker2?.remove()
                    val addr = geocoder.getFromLocationName(result[1], 1)
                    Log.d("ADDR", addr[0].toString())
                    val location: Address = addr[0]
                    val p2 = LatLng(location.latitude, location.longitude)
                    marker2 = mMap.addMarker(MarkerOptions().position(p2)
                        .draggable(true))!!
                    builder.include(marker2!!.position)
                }
                val bounds = builder.build()
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels

                val padding = (width*0.3).toInt() // offset from edges of the map in pixels

                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding))

            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        }
        else{
            //Default map location
            val madrid = LatLng(40.416729, -3.703339)
            marker = mMap.addMarker(MarkerOptions().position(madrid).draggable(true))!!
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(madrid, 15.0F))
        }

        mMap.setOnMapClickListener { latlng -> // Clears the previously touched position

            try{
                if(clicked==0){
                    mMap.clear()
                    clicked=1
                    // Animating to the touched position
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng))

                    val location = LatLng(latlng.latitude, latlng.longitude)
                    address = getAddress(location.latitude, location.longitude)
                    Log.i("ADDR", address)
                    marker = mMap.addMarker(MarkerOptions()
                        .position(location)
                        .draggable(true)
                    )
                    autocompleteFragment!!.setText(address)
                    return@setOnMapClickListener
                }

                if(clicked==1){
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng))

                val location = LatLng(latlng.latitude, latlng.longitude)
                address2 = getAddress(location.latitude, location.longitude)
                Log.i("ADDR", address2)
                marker2 = mMap.addMarker(MarkerOptions()
                    .position(location)
                    .draggable(true)
                )
                autocompleteFragment2!!.setText(address2)
                clicked=2
                }
            }
            catch (ex: Exception){
                Toast.makeText(activity, "Ha ocurrido un problema, comprueba la conexión"
                    , Toast.LENGTH_SHORT)
                Log.e("ERROR Locat", ex.toString())
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMarkerDrag(p0: Marker) {
    }

    override fun onMarkerDragEnd(mark: Marker) {

        if(mark == marker2){
            address2 = getAddress(mark.position.latitude, mark.position.longitude)
            autocompleteFragment2!!.setText(address2)
            //marker2 = mark
        }
        else{
            address = getAddress(mark.position.latitude, mark.position.longitude)
            autocompleteFragment!!.setText(address)
            //marker = mark
        }
    }

    override fun onMarkerDragStart(p0: Marker) {
    }

    //https://medium.com/@trientran/android-working-with-google-maps-and-directions-api-44765433f19
    //Directions API

    override fun onMyLocationButtonClick(): Boolean {
        //Sets the map to the position of the user
        Toast.makeText(activity, "Obteniendo tu posición...", Toast.LENGTH_SHORT).show()
        return false //the default behavior should still occur
    }

    override fun onMyLocationClick(p0: Location) {
        mMap.clear()
        clicked=1
        val markerOptions = MarkerOptions().position(LatLng(p0.latitude, p0.longitude)).
            draggable(true)
        marker = mMap.addMarker(markerOptions)!!
        address = getAddress(p0.latitude, p0.longitude)
        autocompleteFragment!!.setText(address)
        Log.i("ADDR", address)
    }

    private fun getAddress(lat: Double, lng: Double): String { //Get text address from LatLng data
        val geocoder = Geocoder(requireActivity())
        val list = geocoder.getFromLocation(lat, lng, 1)
        return list[0].getAddressLine(0)
    }

}