package com.example.routeplanning

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.routeplanning.BuildConfig.GOOGLE_MAPS_API
import com.example.routeplanning.databinding.FragmentSecondBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */


class SecondFragment: Fragment(), OnMapReadyCallback,
    GoogleMap.OnMyLocationButtonClickListener,
    GoogleMap.OnMarkerDragListener,
    GoogleMap.OnMyLocationClickListener{ //extends Fragment and implements callbacks and listeners

    private var _binding: FragmentSecondBinding? = null
    private var editAddr: FragmentContainerView? = null
    private lateinit var mMap: GoogleMap
    private lateinit var marker: Marker
    private var address = ""

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view: View = inflater.inflate(R.layout.fragment_second, container, false)
        Places.initialize(requireActivity(), GOOGLE_MAPS_API)
        editAddr = view.findViewById(R.id.autocomplete_fragment)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,
            Place.Field.LAT_LNG))

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i("TAG", "Place: ${place.name}, ${place.id}, ${place.latLng}")
                marker = mMap.addMarker(MarkerOptions().position(place.latLng!!))!!
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.latLng!!, 15.0F))
                address = place.name!!.toString()

            }

            override fun onError(status: Status) {
                // TODO: Handle the error.
                Log.i("TAG", "An error occurred: $status")
            }
        })

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        var request = 0
        var result = arrayListOf("", "")

        setFragmentResultListener("requestedAddrO") { _, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            request = 0
            //editAddr!!.setText(result.first().toString())
        }//Origin requested
        setFragmentResultListener("requestedAddrD") { _, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            request = 1
            //editAddr!!.setText(result.last().toString())
        }//Destination requested

        val secondButton: FloatingActionButton = view.findViewById(R.id.buttonSecond)
        secondButton.setOnClickListener {
            //result = editAddr!!.text.toString()
            if (request == 0) {
                //result[0] = editAddr!!.text.toString()
                result[0] = address
                Log.d("List", result.toString())
                setFragmentResult(
                    "directionsRequested",
                    bundleOf("bundleKey" to result)
                )
            }
            if (request == 1) {
                //result[1] = editAddr!!.text.toString()
                result[1] = address
                Log.d("List", result.toString())
                setFragmentResult(
                    "directionsRequested",
                    bundleOf("bundleKey" to result)
                )
            }
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)

        }
        return view

    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap){
        mMap = googleMap
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMarkerDragListener(this)

        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
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

        //Default map location:
        val madrid = LatLng(40.416729, -3.703339)
        marker = mMap.addMarker(MarkerOptions().position(madrid))!!
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(madrid, 15.0F))
        //

        mMap.setOnMapClickListener { latlng -> // Clears the previously touched position
            mMap.clear()
            // Animating to the touched position
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng))

            val location = LatLng(latlng.latitude, latlng.longitude)
            address = getAddress(location.latitude, location.longitude)
            Log.i("ADDR", address)
            mMap.addMarker(MarkerOptions()
                .position(location)
                .draggable(true)
            )
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMarkerDrag(p0: Marker) {
    }

    override fun onMarkerDragEnd(marker: Marker) {
        address = getAddress(marker.position.latitude, marker.position.longitude)
    }

    override fun onMarkerDragStart(p0: Marker) {
    }

    override fun onMyLocationButtonClick(): Boolean {
        //Sets the map to the position of the user
        Toast.makeText(activity, "Obteniendo tu posición...", Toast.LENGTH_SHORT).show()
        return false //the default behavior should still occur
    }

    override fun onMyLocationClick(p0: Location) {
        marker.remove()
        val markerOptions = MarkerOptions().position(LatLng(p0.latitude, p0.longitude))
        marker = mMap.addMarker(markerOptions)!!
        address = getAddress(p0.latitude, p0.longitude)
        Log.i("ADDR", address)

    }

    private fun getAddress(lat: Double, lng: Double): String { //Get text address from LatLng data
        val geocoder = Geocoder(requireActivity())
        val list = geocoder.getFromLocation(lat, lng, 1)
        return list[0].getAddressLine(0)
    }

}