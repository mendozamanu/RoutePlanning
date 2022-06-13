package com.example.routeplanning

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.routeplanning.databinding.FragmentFirstBinding
import com.google.android.material.navigation.NavigationView
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.types.ObjectId

open class Routes(
    @PrimaryKey var _id: ObjectId? = ObjectId(),
    var origin: String = "",
    var destination: String = "",
    var depart: String = "",
    var arrival: String = "",
    var publictransport: Boolean = false,
    var car: Boolean = false,
    var walk: Boolean = false,
    var comment: String = ""

): RealmObject()

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */

var user: User? = null
var syncedRealm: Realm? = null
private var submitted = 0
var routes = MutableList(5){Routes()} //Allowing max 5 routes at first
var routeCount = 0

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private var editOriginAdr: EditText? = null
    private var editDestAdr: EditText? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view: View = inflater.inflate(R.layout.fragment_first, container, false)

        editOriginAdr = view.findViewById(R.id.originAddr)
        editDestAdr = view.findViewById(R.id.destAddr)

        //_binding = FragmentFirstBinding.inflate(layoutInflater)

        val originButton: Button = view.findViewById(R.id.button_first_o)
        val destButton: Button = view.findViewById(R.id.button_first_d)

        //https://developer.android.com/training/basics/fragments/pass-data-between?hl=es-419
        setFragmentResultListener("directionsRequested"){
            key, bundle ->
            val result2 = bundle.getStringArrayList("bundleKey") as ArrayList<String>
            editOriginAdr!!.setText(result2[0])
            editDestAdr!!.setText(result2[1])
        }

        originButton.setOnClickListener {
            val result = arrayListOf(editOriginAdr!!.text.toString(), editDestAdr!!.text.toString())
            setFragmentResult("requestedAddrO", bundleOf("bundledKey" to result))
            Log.d("List", result.toString())
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        destButton.setOnClickListener {
            val result = arrayListOf(editOriginAdr!!.text.toString(), editDestAdr!!.text.toString())
            setFragmentResult("requestedAddrD", bundleOf("bundledKey" to result))

            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
        loginToMongo()

        return view

    }

    private fun loginToMongo(){

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
                            Log.i("RealmOK", "Successfully connected " +
                                    "with realm $syncedRealm")
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

    }

     @SuppressLint("CutPasteId", "SetTextI18n")
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

         val sendButton: Button = view.findViewById(R.id.sendButton)

         sendButton.setOnClickListener {
            //MongoDB connect and send data
            Log.i("RealmOK", "Successfully connected with realm $syncedRealm")
            Log.d("SUBMITTED status", submitted.toString())
            if (submitted == 1) {
                Toast.makeText(
                    activity, "Ruta ya confirmada",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            //Before: data validation
            var data: Routes?

            val org = view.findViewById<TextView>(R.id.editTextTime).text.toString()
            val values: List<String> = org.split(":")
            if (values[0].toInt() <= 23 && values[1].toInt() <= 59) {
                Log.d("Debug1", "DEPART at: $org")
            } else {
                Toast.makeText(
                    activity, "Error en la fecha, compruebe que la hora es correcta" +
                            " XX:XX", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val dest = view.findViewById<TextView>(R.id.editTextTime2).text.toString()
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

            Toast.makeText(activity, "Enviando...", Toast.LENGTH_SHORT).show()
            //submit = 1
            Log.i("Status", "Synced realm: $syncedRealm")
            if (syncedRealm?.isClosed == false) {
                submitted = 1
                routeCount += 1
                val comm = view.findViewById<EditText>(R.id.comments).text.toString()
                val obId = ObjectId()

                syncedRealm?.executeTransaction { transact: Realm ->
                    data = transact.createObject(Routes::class.java, obId)
                    routes[routeCount-1]._id = obId

                    data?.origin = editOriginAdr?.text.toString()
                    routes[routeCount-1].origin = editOriginAdr?.text.toString()

                    data?.destination = editDestAdr?.text.toString()
                    routes[routeCount-1].destination = editDestAdr?.text.toString()

                    data?.depart = org
                    data?.arrival = dest
                    routes[routeCount-1].depart = org
                    routes[routeCount-1].arrival = dest

                    if (view.findViewById<CheckBox>(R.id.checkBox).isChecked) {
                        data?.publictransport = true
                        routes[routeCount-1].publictransport = true
                    }
                    if (view.findViewById<CheckBox>(R.id.checkBox2).isChecked) {
                        data?.car = true
                        routes[routeCount-1].car = true
                    }
                    if (view.findViewById<CheckBox>(R.id.checkBox3).isChecked) {
                        data?.walk = true
                        routes[routeCount-1].walk = true
                    }
                    data?.comment = comm
                    routes[routeCount-1].comment = comm

                }
                activity?.findViewById<NavigationView>(R.id.nav_view)?.menu?.add("Ruta $routeCount")

                val task = syncedRealm!!.where(Routes::class.java)
                    .equalTo("_id", routes[routeCount-1]._id).findFirst()
                Log.v("EXAMPLE", "Fetched object by primary key: $task")

            } else {
                Log.d("REALM error", "realm closed or wrong ref")
                Toast.makeText(activity, "Error al guardar la ruta, revise " +
                        "su conexión a internet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

         val resetButton: Button = view.findViewById(R.id.resetButton)

         resetButton.setOnClickListener {
             view.findViewById<TextView>(R.id.editTextTime)?.text = "00:00"
             view.findViewById<TextView>(R.id.editTextTime2)?.text = "00:00"
             view.findViewById<EditText>(R.id.originAddr)?.setText("")
             view.findViewById<EditText>(R.id.destAddr)?.setText("")
             view.findViewById<CheckBox>(R.id.checkBox)?.isChecked = false
             view.findViewById<CheckBox>(R.id.checkBox2)?.isChecked = false
             view.findViewById<CheckBox>(R.id.checkBox3)?.isChecked = false
             view.findViewById<EditText>(R.id.comments)?.setText("")

             submitted = 0
         }
    }

    fun getRoutes():MutableList<Routes> {
        return routes
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("DEBUG", "Destroyed First frag view")
        _binding = null
    }
}