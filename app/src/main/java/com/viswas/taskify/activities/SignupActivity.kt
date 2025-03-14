
package com.viswas.taskify.activities

import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.viswas.taskify.R
import com.viswas.taskify.firebase.FireStore
import com.viswas.taskify.models.User

class SignupActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)
        setUpActionBar()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnsignup = findViewById<Button>(R.id.btn_sign_up)
        btnsignup.setOnClickListener{
            registerUser()
        }
    }

    fun userRegisteredSuccess(){
        Toast.makeText(this, " you have successfully registered the email address", Toast.LENGTH_LONG).show()
        hideProgressDialog()
        FirebaseAuth.getInstance().signOut()
        finish()
    }
    private fun setUpActionBar(){
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_sign_up_activity)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if(actionBar != null){
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeAsUpIndicator(R.drawable.ic_black_color_back_24dp)
        }

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

    }

    private fun registerUser(){
        val name: String = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_name).text.toString().trim{it<=' '}
        val email: String = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_email).text.toString().trim {it <= ' '}
        val password: String = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_password).text.toString().trim {it <= ' '}

        if(validateForm(name, email, password)){
            showProgressDialog(resources.getString(R.string.please_wait))
            FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email,password)
                .addOnCompleteListener(
                    {
                            task->
                        if(task.isSuccessful){
                            val firebaseUser : FirebaseUser = task.result!!.user!!
                            val registeredEmail = firebaseUser.email!!
                            val user = User(firebaseUser.uid,name,registeredEmail)
                            FireStore().registerUser(this,user)
                        }else{
                            Toast.makeText(this,task.exception!!.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }

    }

    private fun validateForm(name: String, email: String, password: String) : Boolean{
        return when {
            TextUtils.isEmpty(name)-> {
                showErrorSnackBar("Please enter a name")
                false
            }
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