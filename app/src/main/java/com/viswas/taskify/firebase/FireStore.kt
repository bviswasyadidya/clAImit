package com.viswas.taskify.firebase

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.viswas.taskify.activities.LuggageActivity
import com.viswas.taskify.activities.MainActivity
import com.viswas.taskify.activities.ProfileActivity
import com.viswas.taskify.activities.SigninActivity
import com.viswas.taskify.activities.SignupActivity
import com.viswas.taskify.models.Luggage
import com.viswas.taskify.models.User
import com.viswas.taskify.utils.Constants

class FireStore {
    private val mFireStore = FirebaseFirestore.getInstance()

    fun registerUser(activity: SignupActivity, userInfo : User){
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId()).set(userInfo, SetOptions.merge())
            .addOnSuccessListener {
                activity.userRegisteredSuccess()
            }.addOnFailureListener{
                    e->
                Log.e(activity.javaClass.simpleName, "Error writing the document")
            }
    }

    fun registerLuggage(activity: Activity,luggage: Luggage){
        mFireStore.collection(Constants.LUGGAGE)
            .document(luggage.uid)
            .set(luggage,SetOptions.merge())
            .addOnSuccessListener {
                if(activity is LuggageActivity){
                    activity.luggageSavedSuccess()
                }
            }
            .addOnFailureListener {
                    e->
                if(activity is LuggageActivity){
                    activity.hideProgressDialog()
                    Toast.makeText(activity,"Error saving luggage details", Toast.LENGTH_LONG).show()
                }
                Log.e(activity.javaClass.simpleName,"Error registering luggage", e)
            }
    }
    fun loadUserData(activity: Activity){
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId()).get()
            .addOnSuccessListener {document->
                val loggedUser = document.toObject(User::class.java)!!

                when(activity){
                    is SigninActivity->{
                        activity.signInSuccess(loggedUser)
                    }
                    is MainActivity ->{
                        activity.updateNavigationUserDetails(loggedUser)
                    }
                    is ProfileActivity ->{
                        activity.setUserDataInUI(loggedUser)
                    }
                }

            }.addOnFailureListener {
                    e->
                when(activity){
                    is SigninActivity->{
                        activity.hideProgressDialog()
                    }
                    is MainActivity ->{
                        activity.hideProgressDialog()
                    }
                }
                Log.e(activity.javaClass.simpleName, "Error!")
            }
    }
    fun loadLuggageData(activity: LuggageActivity, onLuggageLoaded: (Luggage?) -> Unit) { // Updated to take a callback
        val currentUserId = getCurrentUserId()
        mFireStore.collection(Constants.USERS)
            .document(currentUserId)
            .get()
            .addOnSuccessListener { userDoc ->
                val userEmail = userDoc.getString("email") ?: ""
                mFireStore.collection(Constants.LUGGAGE)
                    .whereEqualTo("email", userEmail)
                    .get()
                    .addOnSuccessListener { luggageQuery ->

                        if (!luggageQuery.isEmpty) {
                            val luggage = luggageQuery.documents[0].toObject(Luggage::class.java)
                            luggage?.let {
                                onLuggageLoaded(it) // Call the callback with the luggage data
                            }
                        } else {
                            onLuggageLoaded(null) // No luggage found
                            Toast.makeText(activity, "No luggage found. Add new luggage.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->

                        Toast.makeText(activity, "Error loading luggage: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("FireStore", "Error fetching luggage", e)
                        onLuggageLoaded(null) // Handle failure by passing null
                    }
            }
            .addOnFailureListener { e ->

                Toast.makeText(activity, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FireStore", "Error fetching user data", e)
                onLuggageLoaded(null) // Handle failure by passing null
            }
    }
    fun getCurrentUserId(): String{
        var currentUser = FirebaseAuth.getInstance().currentUser
        var currentUserID = ""
        if(currentUser != null){
            currentUserID = currentUser.uid
        }
        return currentUserID
    }
    fun updateUserProfileData(activity: ProfileActivity, userHashMap:HashMap<String,Any>){
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId())
            .update(userHashMap)
            .addOnSuccessListener {
                Log.i(activity.javaClass.simpleName,"Profile Data updated Successfully")
                Toast.makeText(activity,"Profile updated successfully",Toast.LENGTH_SHORT).show()
                activity.profileUpdateSuccess()
            }.addOnFailureListener {
                    e->
                activity.hideProgressDialog()
                Log.e(
                    activity.javaClass.simpleName,
                    "Error updating Profile Data",
                    e
                )
                Toast.makeText(activity,"Error on updating profile",Toast.LENGTH_SHORT).show()
            }
    }
    fun getLuggageByUid(uid: String, callback: (Luggage?) -> Unit) {
        mFireStore.collection(Constants.LUGGAGE)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callback(document.toObject(Luggage::class.java))
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                Log.e("FireStore", "Error getting luggage", it)
                callback(null)
            }
    }

    fun getUserByEmail(email: String, callback: (User?) -> Unit) {
        mFireStore.collection(Constants.USERS)
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    callback(documents.documents[0].toObject(User::class.java))
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                Log.e("FireStore", "Error getting user", it)
                callback(null)
            }
    }
}