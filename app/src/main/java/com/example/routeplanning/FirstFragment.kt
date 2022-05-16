package com.example.routeplanning

import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.routeplanning.databinding.FragmentFirstBinding
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.Credentials
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

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private var sharedViewModelInstance: SharedViewModel? = null
    private var editOriginAdr: EditText? = null
    private var editDestAdr: EditText? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view: View = inflater.inflate(R.layout.fragment_first, container, false)

        editOriginAdr = view.findViewById(R.id.originAddr)
        editDestAdr = view.findViewById(R.id.destAddr)

        val originButton: Button = view.findViewById(R.id.button_first_o)
        val destButton: Button = view.findViewById(R.id.button_first_d)

        originButton.setOnClickListener {
            sharedViewModelInstance?.setData(editOriginAdr!!.text)
            Toast.makeText(context, sharedViewModelInstance?.getData().toString(), Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        destButton.setOnClickListener {
            sharedViewModelInstance?.setData(editDestAdr!!.text)
            Toast.makeText(context, sharedViewModelInstance?.getData().toString(), Toast.LENGTH_SHORT).show()
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
                            Log.i("RealmOK", "Successfully connected with realm $syncedRealm")
                        }
                    })

                } else {
                    Toast.makeText(
                        activity,
                        "MongoDB realm authentication error. Try again or contact admin",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        }

    }

     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

         val sendButton: Button = view.findViewById(R.id.sendButton)

         sharedViewModelInstance = ViewModelProvider(this).get(SharedViewModel::class.java)
         sharedViewModelInstance!!.getData().observe(viewLifecycleOwner) {
             editOriginAdr!!.text = it as Editable?
             //binding.destAddr.text = it as Editable?
         }


         sendButton.setOnClickListener {
            //MongoDB connect and send data
            Log.i("RealmOK", "Successfully connected with realm $syncedRealm")
            Log.d("SUBMITTED status", submitted.toString())
            if (submitted == 1) {
                Toast.makeText(
                    activity, "Already submitted",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            //Before: data validation
            //var data: Routes?

            /*val org = binding.editTextTime.text.toString()
            val values: List<String> = org.split(":")
            if (values[0].toInt() <= 23 && values[1].toInt() <= 59) {
                Log.d("Debug1", "DEPART at: $org")
            } else {
                Toast.makeText(
                    activity, "Error on date, check that the time is correct" +
                            " XX:XX", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val dest = binding.editTextTime2.text.toString()
            val values2: List<String> = dest.split(":")
            if (values2[0].toInt() <= 23 && values2[1].toInt() <= 59) {
                Log.d("Debug1", "ARRIVE at: $dest")
            } else {
                Toast.makeText(
                    activity, "Error on date, check that the time is correct" +
                            " XX:XX", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            Toast.makeText(activity, "Submitting...", Toast.LENGTH_SHORT).show()
            //submit = 1
            Log.i("Status", "Synced realm: $syncedRealm")
            if (syncedRealm?.isClosed == false) {
                submitted = 1
                syncedRealm?.executeTransaction { transact: Realm ->
                    data = transact.createObject(Routes::class.java, ObjectId())
                    data?.origin = binding.originAddr.text.toString()
                    data?.destination = binding.destAddr.text.toString()
                    data?.depart = binding.editTextTime.text.toString()
                    data?.arrival = binding.editTextTime2.text.toString()
                    if (binding.checkBox.isChecked) {
                        data?.publictransport = true
                    }
                    if (binding.checkBox2.isChecked) {
                        data?.car = true
                    }
                    if (binding.checkBox3.isChecked) {
                        data?.walk = true
                    }
                    data?.comment = binding.comments.text.toString()
                }

            } else {
                Log.d("REALM error", "realm closed or wrong ref")
                return@setOnClickListener
            }*/
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("DEBUG", "Destroyed First frag view")
        _binding = null
    }
}