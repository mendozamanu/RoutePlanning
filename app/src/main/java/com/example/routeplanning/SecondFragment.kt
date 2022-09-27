package com.example.routeplanning

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.routeplanning.BuildConfig.GOOGLE_MAPS_API
import com.example.routeplanning.databinding.FragmentSecondBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.maps.android.PolyUtil
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.types.ObjectId
import org.json.JSONObject
import java.io.IOException


/**
 * A simple [Fragment] subclass as the destination in the navigation.
 */

open class Routes(
    @PrimaryKey var _id: ObjectId? = ObjectId(),
    var uid: String = "",
    var origin: String = "",
    var destination: String = "",
    var depart: String = "",
    var arrival: String = "",
    var publictransport: Boolean = false,
    var car: Boolean = false,
    var walk: Boolean = false,
    var days: String = ""
//uid: identif para almacenar y restaurar rutas del usuario en futuros usos de la app - deviceID

): RealmObject()

var routes = MutableList(10){Routes()} //Allowing the first 10 routes
var routeCount = 0
var user: User? = null
var syncedRealm: Realm? = null


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
    private var result = arrayListOf("","","","","","")
    private var clicked = 0
    private var polyline = 0
    private var autocompleteFragment : AutocompleteSupportFragment? = null
    private var autocompleteFragment2 : AutocompleteSupportFragment? = null
    private var submitted = 0
    private var checkb1 = false
    private var checkb2 = false
    private var checkb3 = false
    private var androidId:String = ""

    private var geocoder:Geocoder? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i("Loc", "Location ok")
        } else {
            Toast.makeText(activity, "Acepta los permisos de localización para " +
                    "poder guardar tu ubicación actual", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100)
        }

        geocoder = Geocoder(requireActivity())

        val view: View = inflater.inflate(R.layout.fragment_second, container, false)

         androidId = Settings.Secure.getString(
            context?.contentResolver,
            Settings.Secure.ANDROID_ID
        )

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
                Log.e("TAG", "An error occurred: $status")
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
                Log.e("TAG", "An error occurred: $status")
            }
        })

        val timedep = view.findViewById<EditText>(R.id.timeDepart)
        val timearr = view.findViewById<EditText>(R.id.timeArrive)

        timedep?.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
                submitted=0
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
            }
        })

        timearr?.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
                submitted=0
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
            }
        })

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        var days:String
        var transp:String
        val chip: Chip = view.findViewById(R.id.chip_1)
        val chip3: Chip = view.findViewById(R.id.chip_3)
        val chip4: Chip = view.findViewById(R.id.chip_4)
        val chip5: Chip = view.findViewById(R.id.chip_5)
        val chip6: Chip = view.findViewById(R.id.chip_6)
        val chip7: Chip = view.findViewById(R.id.chip_7)
        val chip8: Chip = view.findViewById(R.id.chip_8)
        val chip9: Chip = view.findViewById(R.id.chip_9)

        val spinner: Spinner = view.findViewById(R.id.spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                if(parentView?.getItemAtPosition(position).toString() == "Coche"){
                    checkb1 = true
                    checkb2 = false
                    checkb3 = false
                }
                if(parentView?.getItemAtPosition(position).toString() == "Caminar"){
                    checkb1 = false
                    checkb2 = true
                    checkb3 = false
                }
                if(parentView?.getItemAtPosition(position).toString() == "Transporte público"){
                    checkb1 = false
                    checkb2 = false
                    checkb3 = true
                }
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                checkb1=false
                checkb2=false
                checkb3=false
            }
        }

        chip.setOnClickListener {

            //If invoked when chip is "unchecked" the weekday chips are unmarked too
            chip3.isChecked=chip.isChecked
            chip4.isChecked=chip.isChecked
            chip5.isChecked=chip.isChecked
            chip6.isChecked=chip.isChecked
            chip7.isChecked=chip.isChecked
        }

        val submit: Button = view.findViewById(R.id.save_button)

        submit.setOnClickListener{
            loginToMongo()

            Log.i("RealmOK", "Successfully connected with realm $syncedRealm")
            Log.d("SUBMITTED status", submitted.toString())
            if(address == "" || address2 == ""){
                Toast.makeText(activity, "No se han introducido direcciones de origen y/o " +
                        "destino", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (submitted == 1) {
                Toast.makeText(
                    activity, "Ruta ya confirmada",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            //Before: data validation
            var data: Routes?

            val org = timedep.text.toString()
            val values: List<String> = org.split(":")
            if (values[0].toInt() <= 23 && values[1].toInt() <= 59) {
                Log.i("Depart", "DEPART at: $org")
            } else {
                Toast.makeText(
                    activity, "Error en la fecha, compruebe que la hora es correcta" +
                            " XX:XX", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val dest = timearr.text.toString()
            val values2: List<String> = dest.split(":")
            if (values2[0].toInt() <= 23 && values2[1].toInt() <= 59) {
                Log.d("Debug1", "ARRIVE at: $dest")
            } else {
                Toast.makeText(
                    activity, "Error en la fecha, compruebe que la hora es correcta" +
                            " XX:XX", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            //submit = 1
            Log.i("Status", "Synced realm: $syncedRealm")
            if (syncedRealm?.isClosed == false) {
                if(address!="" && address2!=""){
                    Toast.makeText(activity, "Enviando...", Toast.LENGTH_SHORT).show()
                    routeCount += 1
                    //val comm = view.findViewById<EditText>(R.id.comments).text.toString()
                    val obId = ObjectId()

                    syncedRealm?.executeTransaction { transact: Realm ->
                        data = transact.createObject(Routes::class.java, obId)
                        routes[routeCount - 1]._id = obId

                        data?.origin = address
                        routes[routeCount - 1].origin = address

                        data?.destination = address2
                        routes[routeCount - 1].destination = address2

                        data?.depart = org
                        data?.arrival = dest
                        routes[routeCount - 1].depart = org
                        routes[routeCount - 1].arrival = dest

                        data?.uid = androidId

                        var transport = ""
                        if (checkb1) {
                            data?.publictransport = true
                            transport += "p"
                            routes[routeCount - 1].publictransport = true
                        }
                        if (checkb2) {
                            data?.car = true
                            transport += "c"
                            routes[routeCount - 1].car = true
                        }
                        if (checkb3) {
                            data?.walk = true
                            transport += "w"
                            routes[routeCount - 1].walk = true
                        }
                        var day = ""
                        if (chip3.isChecked) day += (chip3.text.toString())
                        if (chip4.isChecked) day += (chip4.text.toString())
                        if (chip5.isChecked) day += (chip5.text.toString())
                        if (chip6.isChecked) day += (chip6.text.toString())
                        if (chip7.isChecked) day += (chip7.text.toString())
                        if (chip8.isChecked) day += (chip8.text.toString())
                        if (chip9.isChecked) day += (chip9.text.toString())
                        data?.days = day
                        //data?.comment = comm
                        //routes[routeCount-1].comment = comm
                        routes[routeCount - 1].days = day

                        result[0] = address
                        result[1] = address2
                        result[2] = timedep.text.toString()
                        result[3] = timearr.text.toString()
                        result[4] = day
                        result[5] = transport
                        setFragmentResult(
                            "directionsRequested",
                            bundleOf("bundleKey" to result)
                        )
                    }
                    submitted=1
                    activity?.findViewById<NavigationView>(R.id.nav_view)?.menu?.add(1,
                        routeCount-1, routeCount, "$address - $address2")

                    val task = syncedRealm!!.where(Routes::class.java)
                        .equalTo("_id", routes[routeCount-1]._id).findFirst()
                    Log.v("EXAMPLE", "Fetched object by primary key: $task")
                }else {
                    Toast.makeText(activity, "Aviso. No se han introducido datos sobre " +
                            "origen y/o destino", Toast.LENGTH_SHORT).show()
                }

            } else {
                Log.e("REALM error", "realm closed or wrong ref")
                Toast.makeText(activity, "Error al guardar la ruta, revise " +
                        "su conexión a internet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

        }

        val secondButton: FloatingActionButton = view.findViewById(R.id.buttonSecond)
        secondButton.setOnClickListener {
            days=""
            if(chip3.isChecked) days+=(chip3.text.toString())
            if(chip4.isChecked) days+=(chip4.text.toString())
            if(chip5.isChecked) days+=(chip5.text.toString())
            if(chip6.isChecked) days+=(chip6.text.toString())
            if(chip7.isChecked) days+=(chip7.text.toString())
            if(chip8.isChecked) days+=(chip8.text.toString())
            if(chip9.isChecked) days+=(chip9.text.toString())
            transp=""
            if (checkb1) transp += "p"
            if (checkb2) transp += "c"
            if (checkb3) transp += "w"

            if(polyline == 1){ //Clear previous polyline
                mMap.clear()
                polyline=0
            }
            if(address != "" && address2 !=""){

                Toast.makeText(activity, "Calculando ruta...",
                    Toast.LENGTH_SHORT).show()
                result[0] = address
                result[1] = address2
                result[2] = timedep.text.toString()
                result[3] = timearr.text.toString()
                result[4] = days
                result[5] = transp

                marker = mMap.addMarker(MarkerOptions().position(marker!!.position)
                    .draggable(true))
                marker2 = mMap.addMarker(MarkerOptions().position(marker2!!.position)
                    .draggable(true))

                setFragmentResult(
                        "directionsRequested",
                        bundleOf("bundleKey" to result)
                )
                //Route generation from the given points
                var mode = "driving" //Defaults to driving as per api standard
                if (checkb3) {
                    mode="transit"
                }
                if (checkb2) {
                    mode="walking"
                }

                //https://developers.google.com/maps/documentation/directions/get-directions
                val path: MutableList<List<LatLng>> = ArrayList()
                val urlDirections = "https://maps.googleapis.com/maps/api/directions/json?origin="+
                        marker?.position?.latitude.toString()+","+marker?.position?.
                        longitude.toString()+"&destination="+
                        marker2?.position?.latitude.toString()+","+marker2?.position?.
                        longitude.toString()+"&mode=$mode"+"&key="+
                        GOOGLE_MAPS_API
                    //+departure_time or arrival_time according to the user preference

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
            else{
                Toast.makeText(activity, "No se ha proporcionado una ruta válida",
                    Toast.LENGTH_SHORT).show()
            }
            //findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)

        }
        val swapButton: AppCompatImageButton = view.findViewById(R.id.imageButton)
        swapButton.setOnClickListener {
            val aux = address
            address = address2
            address2 = aux
            autocompleteFragment!!.setText(address)
            autocompleteFragment2!!.setText(address2)
        }
        loginToMongo()
        return view

    }

    private fun loginToMongo(): Realm?{

        var synced = syncedRealm
        if (syncedRealm == null) { //not logged in
            context?.let { Realm.init(it) }
            val appID = getString(R.string.app_id)
            val app = App(
                AppConfiguration.Builder(appID)
                    .build()
            )

            val emailPasswordCredentials: Credentials = Credentials.emailPassword(
                getString(R.string.test_email), getString(R.string.test_password)
            )

            app.loginAsync(emailPasswordCredentials) {
                if (it.isSuccess) {
                    Log.i("LoginOK", "Successfully authenticated with test user")
                    user = app.currentUser()
                    val partitionValue = "Routes"
                    val config = SyncConfiguration.Builder(user!!, partitionValue)
                        .allowQueriesOnUiThread(true)
                        .allowWritesOnUiThread(true)
                        .build()
                    //var realm: Realm
                    Realm.getInstanceAsync(config, object : Realm.Callback() {
                        override fun onSuccess(_realm: Realm) {
                            // the realm should live exactly as long as the activity, so assign
                            // the realm to a member variable
                            syncedRealm = _realm
                            synced = syncedRealm
                            Log.i("RealmOK", "Successfully connected " +
                                    "with realm $syncedRealm")
                            val task = syncedRealm!!.where(Routes::class.java)
                                .equalTo("uid", androidId).findAll()
                            for (ruta in task){
                                routeCount+=1
                                activity?.findViewById<NavigationView>(R.id.nav_view)?.menu?.add(1,
                                    routeCount-1, routeCount,
                                    "${ruta.origin} - ${ruta.destination}")
                                routes[routeCount-1]._id = ruta._id
                                routes[routeCount-1].uid = ruta.uid
                                routes[routeCount-1].origin = ruta.origin
                                routes[routeCount-1].destination = ruta.destination
                                routes[routeCount-1].depart = ruta.depart
                                routes[routeCount-1].arrival = ruta.arrival
                                routes[routeCount-1].publictransport = ruta.publictransport
                                routes[routeCount-1].car = ruta.car
                                routes[routeCount-1].walk = ruta.walk
                                routes[routeCount-1].days = ruta.days
                            }
                        }
                    })

                } else {
                    Toast.makeText(
                        activity,
                        "Error de autenticación en MongoDB Realm. Inténtelo de nuevo o " +
                                "revise su conexión a internet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        else{
            return synced
        }

        return synced

    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap){
        mMap = googleMap
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMarkerDragListener(this)
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID

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
            Log.d("RESULT", result.toString())

            try {
                val builder = LatLngBounds.Builder()
                if(result[0]!="") {
                    // May throw an IOException
                    marker?.remove()
                    val addr = geocoder?.getFromLocationName(result[0], 1)
                    Log.d("ADDRESS", addr!![0].toString())
                    val location: Address = addr[0]
                    val p1 = LatLng(location.latitude, location.longitude)
                    marker = mMap.addMarker(MarkerOptions().position(p1)
                        .draggable(true))!!
                    builder.include(marker!!.position)
                }
                if(result[1]!=""){
                    marker2?.remove()
                    val addr = geocoder?.getFromLocationName(result[1], 1)
                    Log.d("ADDRESS", addr!![0].toString())
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
            if(!onMyLocationButtonClick()){
            //Default map location
            val madrid = LatLng(40.416729, -3.703339)
            //marker = mMap.addMarker(MarkerOptions().position(madrid).draggable(true))!!
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(madrid, 10.0F))
            //clicked=1
            } //if the user is located, the map is updated to the user's current position
        }

        autocompleteFragment!!.requireView().findViewById<View>(com.google.android.libraries.
        places.R.id.places_autocomplete_clear_button)
            .setOnClickListener {
                autocompleteFragment!!.setText("")
                Log.d("MARKER", marker.toString())
                if(marker != null) marker!!.remove()
                address=""
                marker=null
                clicked-=1
                it.visibility = View.GONE
            }

        autocompleteFragment2!!.requireView().findViewById<View>(com.google.android.libraries.
        places.R.id.places_autocomplete_clear_button)
            .setOnClickListener {
                autocompleteFragment2!!.setText("")
                Log.d("MARKER2", marker2.toString())
                if(marker2 != null) marker2!!.remove()
                marker2=null
                address2=""
                clicked-=1
                it.visibility = View.GONE
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
                    , Toast.LENGTH_SHORT).show()
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

    @SuppressLint("MissingPermission")
    override fun onMyLocationButtonClick(): Boolean {
        //Sets the map to the position of the user
        var retn = false
        val mFusedLocationClient = LocationServices.
            getFusedLocationProviderClient(requireActivity())
        mFusedLocationClient.lastLocation.addOnCompleteListener(requireActivity()) { task ->
            val location: Location? = task.result
            retn = if (location == null) {
                false
            } else{
                val curr = LatLng(location.latitude, location.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(curr, 15.0F))
                true
            }
        }
        return retn //the default behavior should still occur
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
        return list!![0].getAddressLine(0)
    }

    fun getRoutes():MutableList<Routes> {
        return routes
    }

    fun fillText(frag: Int, t: String){

        if(frag==1 && autocompleteFragment!=null){
            autocompleteFragment!!.setText(t)
            Log.i("Origin", t)
            address = t
            marker?.remove()
            val addr = geocoder?.getFromLocationName(t, 1)
            val location: Address = addr!![0]
            val p1 = LatLng(location.latitude, location.longitude)
            marker = mMap.addMarker(MarkerOptions().position(p1)
                .draggable(true))!!
        }
        if(frag==2 && autocompleteFragment2!=null){
            autocompleteFragment2!!.setText(t)
            Log.i("Destination", t)
            address2 = t
            marker2?.remove()
            val addr = geocoder?.getFromLocationName(t, 1)
            val location: Address = addr!![0]
            val p1 = LatLng(location.latitude, location.longitude)
            marker2 = mMap.addMarker(MarkerOptions().position(p1)
                .draggable(true))!!
        }
    }

}

