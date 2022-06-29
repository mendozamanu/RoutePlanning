package com.example.routeplanning

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
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
    private lateinit var myFragment: FirstFragment
    private lateinit var mitem: MenuItem

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


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.map){
            findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.action_FirstFragment_to_SecondFragment)
            mitem = item
            mitem.isVisible = false
        }

        return true
        //return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        myFragment = FirstFragment() //binding.root is the view of the first fragment

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

        for (i in myFragment.getRoutes()[item.itemId].days.indices){
                when(myFragment.getRoutes()[item.itemId].days[i].toString()){
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

    override fun onBackPressed() {
        mitem.isVisible=true
        super.onBackPressed()
    }
}