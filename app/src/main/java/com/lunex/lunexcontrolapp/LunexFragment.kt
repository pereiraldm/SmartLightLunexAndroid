package com.lunex.lunexcontrolapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.activityViewModels

class LunexFragment : Fragment() {
    private val viewModel: BluetoothViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lunex, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.facebookIcon).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pt-br.facebook.com/lunextecnologia/"))
            startActivity(intent)
        }

        view.findViewById<ImageView>(R.id.instagramIcon).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/lunextecnologia/"))
            startActivity(intent)
        }

        view.findViewById<ImageView>(R.id.linkedinIcon).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/company/lunex-tecnologia/"))
            startActivity(intent)
        }

        val linearLayoutVid: LinearLayout = view.findViewById(R.id.linearLayout6)
        linearLayoutVid.setOnClickListener {
            // Quando você tiver o link do vídeo, substitua o URL abaixo.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/5czw-sAOq2U"))
            startActivity(intent)
        }

        val linearLayoutCat: LinearLayout = view.findViewById(R.id.linearLayout9)
        linearLayoutCat.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.lunex.com.br/images/catalogos/catalogo.pdf"))
            startActivity(intent)
        }

        val linearLayoutWpp: LinearLayout = view.findViewById(R.id.linearLayout5)
        linearLayoutWpp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/5541995110399") // Substitua SEU_NUMERO_DO_WHATSAPP pelo número de telefone da empresa.
            startActivity(intent)
        }

        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                // Update UI to reflect connected status
                Log.d("BluetoothFragment", "Device is connected")
            } else {
                // Update UI to reflect disconnected status
                Log.d("BluetoothFragment", "Device is disconnected")
            }
        }
    }
}
