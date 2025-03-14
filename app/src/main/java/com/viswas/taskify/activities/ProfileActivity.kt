package com.viswas.taskify.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.viswas.taskify.R
import com.viswas.taskify.firebase.FireStore
import com.viswas.taskify.models.User
import com.viswas.taskify.utils.Constants
import de.hdodenhof.circleimageview.CircleImageView
import java.io.IOException

class ProfileActivity : BaseActivity() {

    private var mSelectedImageFileUri: Uri? = null
    private lateinit var ivUserImageView: CircleImageView
    private lateinit var mUserDetails: User
    private var mProfileImageURL: String = ""

    companion object {
        private const val READ_STORAGE_PERMISSION_CODE = 1
        private const val PICK_IMAGE_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setupActionBar()
        FireStore().loadUserData(this)

        ivUserImageView = findViewById(R.id.iv_user_image)
        ivUserImageView.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+ (API 33+), use READ_MEDIA_IMAGES
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    showImageChooser()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                        READ_STORAGE_PERMISSION_CODE
                    )
                }
            } else {
                // For Android 12 and below, use READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    showImageChooser()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        READ_STORAGE_PERMISSION_CODE
                    )
                }
            }
        }
        findViewById<android.widget.Button>(R.id.btn_update)?.setOnClickListener {
            if (mSelectedImageFileUri != null) {
                uploadUserImage()
            } else {
                showProgressDialog(resources.getString(R.string.please_wait))
                updateUserProfileData()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == PICK_IMAGE_REQUEST_CODE && data?.data != null) {
            mSelectedImageFileUri = data.data
            try {
                Glide.with(this)
                    .load(mSelectedImageFileUri)
                    .centerCrop()
                    .placeholder(R.drawable.ic_user_place_holder)
                    .into(ivUserImageView)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted! You can now select an image.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission Denied. Enable it in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showImageChooser() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST_CODE)
    }

    private fun setupActionBar() {
        val toolbar =
            findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_my_profile_activity)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setHomeAsUpIndicator(R.drawable.ic_white_color_back_24dp)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun setUserDataInUI(user: User) {
        mUserDetails = user
        val ivUserImage: CircleImageView = findViewById(R.id.iv_user_image)
        val name = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_name)
        val email = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_email)
        val mob = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_mobile)



        Glide.with(this)
            .load(user.image)
            .fitCenter()
            .placeholder(R.drawable.ic_user_place_holder)
            .into(ivUserImage)

        name.setText(user.name)
        email.setText(user.email)
        if (user.mobile != 0L) {
            mob.setText(user.mobile.toString())
        }
    }

    private fun uploadUserImage(){
        if (mSelectedImageFileUri == null) return
        showProgressDialog(resources.getString(R.string.please_wait))
        MediaManager.get().upload(mSelectedImageFileUri)
            .unsigned("taskify_profile") // Create this preset in Cloudinary dashboard
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    mProfileImageURL = resultData["secure_url"] as String
                    updateUserProfileData()
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    hideProgressDialog()
                    showErrorSnackBar("Upload failed: ${error.description}")
                }
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onStart(requestId: String) {}
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun updateUserProfileData() {
        if (!::mUserDetails.isInitialized) {
            Toast.makeText(this, "User details not loaded yet. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        val userHashMap = HashMap<String, Any>()
        if (mProfileImageURL.isNotEmpty() && mProfileImageURL != mUserDetails.image) {
            userHashMap[Constants.IMAGE] = mProfileImageURL
        }

        val newName = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_name).text.toString()
        val newMobile = findViewById<androidx.appcompat.widget.AppCompatEditText>(R.id.et_mobile).text.toString()
        if (newName != mUserDetails.name) userHashMap[Constants.NAME] = newName
        if (newMobile != mUserDetails.mobile.toString()) userHashMap[Constants.MOBILE] = newMobile.toLong()

        FireStore().updateUserProfileData(this, userHashMap)
    }

    fun profileUpdateSuccess() {
        hideProgressDialog()
        setResult(Activity.RESULT_OK)
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }
}