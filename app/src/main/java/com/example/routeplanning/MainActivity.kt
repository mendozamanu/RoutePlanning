package com.example.routeplanning

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.routeplanning.databinding.ActivityMainBinding

class SharedViewModel: ViewModel(){
    // Mutable LiveData which observed by LiveData
    // and updated to EditTexts when it is changed.
    private val mutableLiveData: MutableLiveData<CharSequence> = MutableLiveData()

    // function to set the changed
    // data from the EditTexts
    fun setData(input: CharSequence) {
        mutableLiveData.value = input
    }

    // function to get the changed data from the EditTexts
    fun getData(): MutableLiveData<CharSequence> = mutableLiveData
}

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}