package com.viswas.taskify.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viswas.taskify.R

class IntroActivity : BaseActivity() {
    @Suppress
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_intro)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val btnSignup = findViewById<Button>(R.id.btn_sign_up_intro)
        btnSignup.setOnClickListener{
            startActivity(Intent(this, SignupActivity::class.java))
        }

        val btnSignin = findViewById<Button>(R.id.btn_sign_in_intro)
        btnSignin.setOnClickListener{
            startActivity(Intent(this, SigninActivity::class.java))
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}