

package com.viswas.taskify.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.viswas.taskify.R
import com.viswas.taskify.models.User

class SigninActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signin)

        auth = FirebaseAuth.getInstance()
        val btnSignin = findViewById<Button>(R.id.btn_sign_in)
        btnSignin.setOnClickListener{
            signInRegisteredUser()
        }
        setActionBar()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setActionBar(){
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_sign_in_activity)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if(actionBar != null){
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeAsUpIndicator(R.drawable.ic_black_color_back_24dp)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun signInRegisteredUser(){
        val email: String = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_email).text.toString().trim {it <= ' '}
        val password: String = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_password).text.toString().trim {it <= ' '}

        if(validateForm(email, password)){
            showProgressDialog(resources.getString(R.string.please_wait))
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    hideProgressDialog()
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("Sign-in", "signInWithEmail:success")
                        val user = auth.currentUser
                        startActivity(Intent(this,MainActivity::class.java))
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("Sign-in", "signInWithEmail:failure", task.exception)
                        Toast.makeText(
                            baseContext,
                            "Authentication failed.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
        }

    }
    fun signInSuccess(user: User){
        hideProgressDialog()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    private fun validateForm(email: String, password: String) : Boolean{
        return when {
            TextUtils.isEmpty(email)-> {
                showErrorSnackBar("Please enter an email address")
                false
            }
            TextUtils.isEmpty(password)-> {
                showErrorSnackBar("Please enter a password")
                false
            }
            else->{
                true
            }
        }

    }
}