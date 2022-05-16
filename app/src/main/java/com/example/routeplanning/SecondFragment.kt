package com.example.routeplanning

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.routeplanning.databinding.FragmentSecondBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private var editAddr: EditText? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view: View = inflater.inflate(R.layout.fragment_second, container, false)

        editAddr = view.findViewById(R.id.editAddress)

        var request = 0
        var result = arrayListOf("","")

        setFragmentResultListener("requestedAddrO"){
                key, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            request = 0
            editAddr!!.setText(result.first().toString())
        }//Origin requested
        setFragmentResultListener("requestedAddrD"){
                key, bundle ->
            result = bundle.getStringArrayList("bundledKey") as ArrayList<String>
            request = 1
            editAddr!!.setText(result.last().toString())
        }//Destination requested

        val secondButton: FloatingActionButton = view.findViewById(R.id.buttonSecond)
        secondButton.setOnClickListener {
            //result = editAddr!!.text.toString()
            if(request == 0){
                result[0] = editAddr!!.text.toString()
                Log.d("List", result.toString())
                setFragmentResult("directionsRequested",
                    bundleOf("bundleKey" to result))
            }
            if(request == 1){
                result[1] = editAddr!!.text.toString()
                Log.d("List", result.toString())
                setFragmentResult("directionsRequested",
                    bundleOf("bundleKey" to result))
            }
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)

        }
        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Toast.makeText(context, "Map to be loaded next iteration", Toast.LENGTH_SHORT).show()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}