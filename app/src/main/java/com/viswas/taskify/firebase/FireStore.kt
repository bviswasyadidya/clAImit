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

    fun registerUser(activity: SignupActivity, userInfo: User) {
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId()).set(userInfo, SetOptions.merge())
            .addOnSuccessListener {
                activity.userRegisteredSuccess()
            }.addOnFailureListener { e ->
                Log.e(activity.javaClass.simpleName, "Error writing the document", e)
            }
    }

    fun registerLuggage(activity: Activity, luggage: Luggage) {
        mFireStore.collection(Constants.LUGGAGE)
            .document(luggage.uid)
            .set(luggage, SetOptions.merge())
            .addOnSuccessListener {
                if (activity is LuggageActivity) {
                    activity.luggageSavedSuccess()
                }
            }
            .addOnFailureListener { e ->
                if (activity is LuggageActivity) {
                    activity.hideProgressDialog()
                    Toast.makeText(activity, "Error saving luggage details", Toast.LENGTH_LONG).show()
                }
                Log.e(activity.javaClass.simpleName, "Error registering luggage", e)
            }
    }

    fun loadUserData(activity: Activity) {
        val currentUserId = getCurrentUserId()
        val auth = FirebaseAuth.getInstance()
        Log.d("FireStore", "Before querying Firestore - User ID: $currentUserId, Auth UID: ${auth.currentUser?.uid ?: "Not signed in"}")
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId()).get()
            .addOnSuccessListener { document ->
                val loggedUser = document.toObject(User::class.java)!!

                when (activity) {
                    is SigninActivity -> {
                        activity.signInSuccess(loggedUser)
                    }
                    is MainActivity -> {
                        activity.updateNavigationUserDetails(loggedUser)
                    }
                    is ProfileActivity -> {
                        activity.setUserDataInUI(loggedUser)
                    }
                }
            }.addOnFailureListener { e ->
                when (activity) {
                    is SigninActivity -> {
                        activity.hideProgressDialog()
                    }
                    is MainActivity -> {
                        activity.hideProgressDialog()
                    }
                }
                Log.e(activity.javaClass.simpleName, "Error!", e)
            }
    }

    fun loadLuggageData(activity: LuggageActivity, onLuggageLoaded: (Luggage?) -> Unit) {
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
                                onLuggageLoaded(it)
                            }
                        } else {
                            onLuggageLoaded(null)
                            Toast.makeText(activity, "No luggage found. Add new luggage.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(activity, "Error loading luggage: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("FireStore", "Error fetching luggage", e)
                        onLuggageLoaded(null)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FireStore", "Error fetching user data", e)
                onLuggageLoaded(null)
            }
    }

    fun getCurrentUserId(): String {
        var currentUser = FirebaseAuth.getInstance().currentUser
        var currentUserID = ""
        if (currentUser != null) {
            currentUserID = currentUser.uid
        }
        return currentUserID
    }

    fun updateUserProfileData(activity: ProfileActivity, userHashMap: HashMap<String, Any>) {
        mFireStore.collection(Constants.USERS)
            .document(getCurrentUserId())
            .update(userHashMap)
            .addOnSuccessListener {
                Log.i(activity.javaClass.simpleName, "Profile Data updated Successfully")
                Toast.makeText(activity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                activity.profileUpdateSuccess()
            }.addOnFailureListener { e ->
                activity.hideProgressDialog()
                Log.e(
                    activity.javaClass.simpleName,
                    "Error updating Profile Data",
                    e
                )
                Toast.makeText(activity, "Error on updating profile", Toast.LENGTH_SHORT).show()
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
            .addOnFailureListener { e ->
                Log.e("FireStore", "Error getting luggage", e)
                callback(null)
            }
    }

    fun getUserByEmail(email: String, callback: (User?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUserEmail = auth.currentUser?.email
        Log.d("FireStore", "Querying users by email: $email, Auth Email: ${currentUserEmail ?: "Not signed in"}")
        if (currentUserEmail == email) {
            mFireStore.collection(Constants.USERS)
                .document(auth.currentUser?.uid ?: "")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val user = document.toObject(User::class.java)
                        Log.d("FireStore", "User found by email: ${user?.email}")
                        callback(user)
                    } else {
                        Log.d("FireStore", "No user found with email: $email")
                        callback(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FireStore", "Error getting user by email: $email", e)
                    callback(null)
                }
        } else {
            Log.w("FireStore", "Unauthorized access attempt for email: $email, Auth Email: $currentUserEmail")
            callback(null) // Deny access to other users' data
        }
    }
}