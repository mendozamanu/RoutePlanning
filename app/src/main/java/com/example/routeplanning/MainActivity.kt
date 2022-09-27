package com.example.routeplanning

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.routeplanning.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private  var myFragment: Fragment? = null
    private var mitem: MenuItem? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        while(!checkPermissions()) {
            requestPermissions()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        drawerLayout = binding.drawerLayout
        navView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        val appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        findViewById<Toolbar>(R.id.toolbar)
            .setupWithNavController(navController, appBarConfiguration)

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)
        myFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        myFragment = myFragment?.childFragmentManager!!.fragments[0]

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.map){
            mitem = item
            mitem?.isVisible = false
        }

        return true
        //return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        val size: Int = navView.menu.size()
        for (i in 0 until size) {
            navView.menu.getItem(i).isChecked = false
        } //uncheck all the items so that only 1 item is checked at a time
        drawerLayout.closeDrawers()
        item.isChecked=true
        myFragment = myFragment as SecondFragment

        Log.d("CHECKED", navView.menu.getItem(item.itemId+1).toString())

        (myFragment as SecondFragment).fillText(1,
            (myFragment as SecondFragment).getRoutes()[item.itemId].origin)
        (myFragment as SecondFragment).fillText(2,
            (myFragment as SecondFragment).getRoutes()[item.itemId].destination)

        binding.root.findViewById<TextView>(R.id.timeDepart)?.text = (myFragment as SecondFragment).
        getRoutes()[item.itemId].depart
        binding.root.findViewById<TextView>(R.id.timeArrive)?.text = (myFragment as SecondFragment).
        getRoutes()[item.itemId].arrival

        if((myFragment as SecondFragment).getRoutes()[item.itemId].car) {
            binding.root.findViewById<Spinner>(R.id.spinner)?.setSelection(0)
        }
        if((myFragment as SecondFragment).getRoutes()[item.itemId].walk){
            binding.root.findViewById<Spinner>(R.id.spinner)?.setSelection(1)
        }
        if((myFragment as SecondFragment).getRoutes()[item.itemId].publictransport){
            binding.root.findViewById<Spinner>(R.id.spinner)?.setSelection(2)
        }

        for (i in (myFragment as SecondFragment).getRoutes()[item.itemId].days.indices){
                when((myFragment as SecondFragment).getRoutes()[item.itemId].days[i].toString()){
                    binding.root.findViewById<Chip>(R.id.chip_3)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_3)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_4)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_4)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_5)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_5)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_6)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_6)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_7)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_7)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_8)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_8)?.isChecked = true
                    binding.root.findViewById<Chip>(R.id.chip_9)?.text -> binding.root.
                    findViewById<Chip>(R.id.chip_9)?.isChecked = true
                }
            }

        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            42
        )
    }

}