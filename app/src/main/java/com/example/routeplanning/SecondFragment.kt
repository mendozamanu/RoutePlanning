package com.example.routeplanning

import android.os.Bundle
import android.text.Editable
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
import com.example.routeplanning.databinding.FragmentSecondBinding
import com.google.android.gms.maps.MapView
import com.google.android.material.floatingactionbutton.FloatingActionButton


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private var sharedViewModelInstance: SharedViewModel? = null
    private var editAddr: EditText? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view: View = inflater.inflate(R.layout.fragment_second, container, false)

        editAddr = view.findViewById(R.id.editAddress)

        val secondButton: FloatingActionButton = view.findViewById(R.id.buttonSecond)
        secondButton.setOnClickListener {
            sharedViewModelInstance?.setData(editAddr!!.text)
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)

        }
        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModelInstance = ViewModelProvider(this).get(SharedViewModel::class.java)
        sharedViewModelInstance!!.getData().observe(viewLifecycleOwner, Observer {
            editAddr!!.text = it as Editable?
        })
        //Toast.makeText(context, binding.editAddress.text.toString(), Toast.LENGTH_SHORT).show()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}