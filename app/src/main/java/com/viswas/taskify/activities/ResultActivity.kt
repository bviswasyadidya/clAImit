package com.viswas.taskify.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.viswas.taskify.R
import java.io.File

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)

        val userName = intent.getStringExtra("USER_NAME")
        val luggageUid = intent.getStringExtra("LUGGAGE_UID")
        val imagePath = intent.getStringExtra("OBJECT_IMAGE_PATH")

        val tvResult = findViewById<TextView>(R.id.tv_result)
        tvResult.text = "$userName, your luggage has been identified with UID: $luggageUid"

        val ivDetectedObject = findViewById<ImageView>(R.id.iv_detected_object)
        if (!imagePath.isNullOrEmpty()) {
            Glide.with(this)
                .load(File(imagePath))
                .centerCrop()
                .into(ivDetectedObject)
        }

        // Apply window insets to the root view
        val rootView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}