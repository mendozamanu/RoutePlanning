package com.example.routeplanning

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.routeplanning.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var myFragment: FirstFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        val appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)

        findViewById<Toolbar>(R.id.toolbar)
            .setupWithNavController(navController, appBarConfiguration)

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        //Opcion 1: override onmenuclick listener, conectar a mongodb y modificar los campos del
        //fragmento 1 al seleccionar una entrada de ruta - problema: como acceder a elem de fragm
        //navView.menu.add("Prueba")

        //Log.d("Routes", myFragment.getRoutes().toString())

    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        myFragment = FirstFragment() //binding.root is the view of the first fragment

        Log.d("ITEM", item.itemId.toString())

        binding.root.findViewById<TextView>(R.id.editTextTime)?.text = myFragment.
        getRoutes()[item.itemId].depart
        binding.root.findViewById<TextView>(R.id.editTextTime2)?.text = myFragment.
        getRoutes()[item.itemId].arrival
        binding.root.findViewById<EditText>(R.id.originAddr)?.setText(myFragment.
        getRoutes()[item.itemId].origin)
        binding.root.findViewById<EditText>(R.id.destAddr)?.setText(myFragment.
        getRoutes()[item.itemId].destination)
        binding.root.findViewById<CheckBox>(R.id.checkBox)?.isChecked = myFragment.
        getRoutes()[item.itemId].publictransport
        binding.root.findViewById<CheckBox>(R.id.checkBox2)?.isChecked = myFragment.
        getRoutes()[item.itemId].car
        binding.root.findViewById<CheckBox>(R.id.checkBox3)?.isChecked = myFragment.
        getRoutes()[item.itemId].walk
        binding.root.findViewById<EditText>(R.id.comments)?.setText(myFragment.
        getRoutes()[item.itemId].comment)

        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}