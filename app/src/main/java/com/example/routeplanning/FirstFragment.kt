package com.example.routeplanning

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.routeplanning.databinding.FragmentFirstBinding
import com.google.android.material.chip.Chip
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
    var comment: String = "",
    var days: String = ""

): RealmObject()

var user: User? = null
var syncedRealm: Realm? = null
private var submitted = 0
var routes = MutableList(5){Routes()} //Allowing max 5 routes at first
var routeCount = 0

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private var editOriginAdr: EditText? = null
    private var editDestAdr: EditText? = null
    private var org: EditText? = null
    private var dest: EditText? = null
    private var days = ""
    private var transp = ""

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
        org = view.findViewById(R.id.editTextTime)
        dest = view.findViewById(R.id.editTextTime2)

        //_binding = FragmentFirstBinding.inflate(layoutInflater)

        val originButton: Button = view.findViewById(R.id.button_first_o)
        val destButton: Button = view.findViewById(R.id.button_first_d)
        val chip3: Chip = view.findViewById(R.id.chip_3)
        val chip4: Chip = view.findViewById(R.id.chip_4)
        val chip5: Chip = view.findViewById(R.id.chip_5)
        val chip6: Chip = view.findViewById(R.id.chip_6)
        val chip7: Chip = view.findViewById(R.id.chip_7)
        val chip8: Chip = view.findViewById(R.id.chip_8)
        val chip9: Chip = view.findViewById(R.id.chip_9)

        val checkb1: CheckBox = view.findViewById(R.id.checkBox)
        val checkb2: CheckBox = view.findViewById(R.id.checkBox2)
        val checkb3: CheckBox = view.findViewById(R.id.checkBox3)

        //https://developer.android.com/training/basics/fragments/pass-data-between?hl=es-419
        setFragmentResultListener("directionsRequested") { _, bundle ->
            val result2 = bundle.getStringArrayList("bundleKey") as ArrayList<String>
            editOriginAdr!!.setText(result2[0])
            editDestAdr!!.setText(result2[1])
            org!!.setText(result2[2])
            dest!!.setText(result2[3])
            days = (result2[4])
            transp = (result2[5])
            if (days != "") {
                for (i in days.indices) {
                    when (days[i].toString()) {
                        chip3.text -> chip3.isChecked = true
                        chip4.text -> chip4.isChecked = true
                        chip5.text -> chip5.isChecked = true
                        chip6.text -> chip6.isChecked = true
                        chip7.text -> chip7.isChecked = true
                        chip8.text -> chip8.isChecked = true
                        chip9.text -> chip9.isChecked = true
                    }
                }
            }
            if (transp != "") {
                for (i in transp.indices) {
                    when (transp[i].toString()) {
                        "p" -> checkb1.isChecked = true
                        "c" -> checkb2.isChecked = true
                        "w" -> checkb3.isChecked = true
                    }
                }
            }
        }

            editOriginAdr?.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    submitted = 0
                    //editOriginAdr!!.text = s
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
            editDestAdr?.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    submitted = 0
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
            org?.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    submitted = 0
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
            dest?.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    submitted = 0
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

            originButton.setOnClickListener {
                //Chip1 & 2 are helpers to autofill other chips
                if (chip3.isChecked) days += (chip3.text.toString())
                if (chip4.isChecked) days += (chip4.text.toString())
                if (chip5.isChecked) days += (chip5.text.toString())
                if (chip6.isChecked) days += (chip6.text.toString())
                if (chip7.isChecked) days += (chip7.text.toString())
                if (chip8.isChecked) days += (chip8.text.toString())
                if (chip9.isChecked) days += (chip9.text.toString())

                if (checkb1.isChecked) transp += "p"
                if (checkb2.isChecked) transp += "c"
                if (checkb3.isChecked) transp += "w"

                val result = arrayListOf(
                    editOriginAdr!!.text.toString(), editDestAdr!!.text.toString(),
                    org!!.text.toString(), dest!!.text.toString(), days, transp
                )
                setFragmentResult("requestedAddrO", bundleOf("bundledKey" to result))
                Log.d("List", result.toString())
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                val item = requireActivity().findViewById<ActionMenuItemView>(R.id.map)
                if (item != null) {
                    item.isVisible = false
                }

            }

            destButton.setOnClickListener {
                if (chip3.isChecked) days += (chip3.text.toString())
                if (chip4.isChecked) days += (chip4.text.toString())
                if (chip5.isChecked) days += (chip5.text.toString())
                if (chip6.isChecked) days += (chip6.text.toString())
                if (chip7.isChecked) days += (chip7.text.toString())
                if (chip8.isChecked) days += (chip8.text.toString())
                if (chip9.isChecked) days += (chip9.text.toString())

                if (checkb1.isChecked) transp += "p"
                if (checkb2.isChecked) transp += "c"
                if (checkb3.isChecked) transp += "w"

                val result = arrayListOf(
                    editOriginAdr!!.text.toString(), editDestAdr!!.text.toString(),
                    org!!.text.toString(), dest!!.text.toString(), days, transp
                )
                setFragmentResult("requestedAddrD", bundleOf("bundledKey" to result))
                Log.d("List", result.toString())
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                val item = requireActivity().findViewById<ActionMenuItemView>(R.id.map)
                if (item != null) {
                    item.isVisible = false
                }
            }
            loginToMongo()
        return view
    }

    fun loginToMongo(): Realm?{

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

     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

         val sendButton: Button = view.findViewById(R.id.sendButton)
         val chip: Chip = view.findViewById(R.id.chip_1)
         val chip2: Chip = view.findViewById(R.id.chip_2)
         val chip3: Chip = view.findViewById(R.id.chip_3)
         val chip4: Chip = view.findViewById(R.id.chip_4)
         val chip5: Chip = view.findViewById(R.id.chip_5)
         val chip6: Chip = view.findViewById(R.id.chip_6)
         val chip7: Chip = view.findViewById(R.id.chip_7)
         val chip8: Chip = view.findViewById(R.id.chip_8)
         val chip9: Chip = view.findViewById(R.id.chip_9)

         chip.setOnClickListener {
             Log.i("WD", "Weekdays")
             //If invoked when chip is "dechecked" the weekday chips are demarked too
             chip3.isChecked=chip.isChecked
             chip4.isChecked=chip.isChecked
             chip5.isChecked=chip.isChecked
             chip6.isChecked=chip.isChecked
             chip7.isChecked=chip.isChecked
         }

         chip2.setOnClickListener {
             Log.i("WN", "Weekend")
             chip8.isChecked=chip2.isChecked
             chip9.isChecked=chip2.isChecked
         }


         sendButton.setOnClickListener {
             //MongoDB connected and send data
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
                 Log.i("Depart", "DEPART at: $org")
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
                     var day = ""
                     if(chip3.isChecked) day+=(chip3.text.toString())
                     if(chip4.isChecked) day+=(chip4.text.toString())
                     if(chip5.isChecked) day+=(chip5.text.toString())
                     if(chip6.isChecked) day+=(chip6.text.toString())
                     if(chip7.isChecked) day+=(chip7.text.toString())
                     if(chip8.isChecked) day+=(chip8.text.toString())
                     if(chip9.isChecked) day+=(chip9.text.toString())
                     data?.days = day
                     data?.comment = comm
                     routes[routeCount-1].comment = comm
                     routes[routeCount-1].days = day

                 }
                 activity?.findViewById<NavigationView>(R.id.nav_view)?.menu?.add(1,
                     routeCount-1, routeCount, "Ruta $routeCount")

                 val task = syncedRealm!!.where(Routes::class.java)
                     .equalTo("_id", routes[routeCount-1]._id).findFirst()
                 Log.v("EXAMPLE", "Fetched object by primary key: $task")

             } else {
                 Log.e("REALM error", "realm closed or wrong ref")
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
             view.findViewById<CheckBox>(R.id.chip_1)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_2)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_3)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_4)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_5)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_6)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_7)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_8)?.isChecked = false
             view.findViewById<CheckBox>(R.id.chip_9)?.isChecked = false
             view.findViewById<EditText>(R.id.comments)?.setText("")

             submitted = 0
         }
    }

    fun getRoutes():MutableList<Routes> {
        return routes
    }

    fun submittedStatus():Int{
        return submitted
    }

    fun submit(subm:Int){
        submitted = subm
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("DEBUG", "Destroyed First frag view")
        _binding = null
    }
}