package com.viswas.taskify.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.viswas.taskify.R
import com.viswas.taskify.firebase.FireStore
import com.viswas.taskify.models.Luggage

import java.io.File

class LuggageActivity : BaseActivity() {
    private var imageFile: File? = null
    private val READ_STORAGE_PERMISSION_CODE = 1
    private var currentLuggage: Luggage? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_luggage)
        setupActionBar()
        FireStore().loadLuggageData(this) { luggage ->
            if (luggage != null) {
                setLuggageDataInUI(luggage)
            } else {
                Toast.makeText(this, "No luggage found. Add new luggage.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveLuggage()
        }

        findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.iv_luggage_image).setOnClickListener {
            requestStoragePermission()
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(findViewById(R.id.toolbar_luggage_activity))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_black_color_back_24dp)
            title = "Luggage Details"
        }
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_luggage_activity)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                pickImage()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    READ_STORAGE_PERMISSION_CODE
                )
            }
        } else {
            // Android 12 and below (including Android 11)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pickImage()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    READ_STORAGE_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                imageFile = File(externalCacheDir, "luggage_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    imageFile?.outputStream()?.use { output -> input.copyTo(output) }
                }
                Glide.with(this)
                    .load(imageFile)
                    .into(findViewById(R.id.iv_luggage_image))
            }
        }
    }
    private fun setLuggageDataInUI(luggage: Luggage) {
        findViewById<EditText>(R.id.et_uid).setText(luggage.uid)
        findViewById<EditText>(R.id.et_email).setText(luggage.email)
        findViewById<EditText>(R.id.et_color).setText(luggage.color)
        Glide.with(this)
            .load(luggage.image)
            .placeholder(R.drawable.ic_user_place_holder)
            .into(findViewById(R.id.iv_luggage_image))
        currentLuggage = luggage
    }

    private fun saveLuggage() {
        val uid = findViewById<EditText>(R.id.et_uid).text.toString().trim()
        val email = findViewById<EditText>(R.id.et_email).text.toString().trim()
        val color = findViewById<EditText>(R.id.et_color).text.toString().trim()

        // Check if data has loaded before saving
        if (currentLuggage == null && (uid.isEmpty() || email.isEmpty())) {
            showErrorSnackBar("Please wait for data to load or fill UID and email")
            return
        }

        // Use currentLuggage if available, otherwise use form data
        val existingUid = currentLuggage?.uid ?: uid
        val existingEmail = currentLuggage?.email ?: email
        val existingColor = currentLuggage?.color ?: color
        val existingImage = currentLuggage?.image ?: ""

        // Validate required fields
        if (existingUid.isEmpty() || existingEmail.isEmpty()) {
            showErrorSnackBar("Please fill UID and email")
            return
        }

        // Ensure an image exists (new or existing)
        if (imageFile == null && existingImage.isEmpty()) {
            showErrorSnackBar("Please select an image")
            return
        }

        showProgressDialog("Saving luggage...")
        if (imageFile != null) {
            // If a new image is picked, upload it to Cloudinary
            MediaManager.get().upload(imageFile!!.absolutePath)
                .unsigned("taskify_luggage")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val imageUrl = resultData["secure_url"] as String
                        val luggage = Luggage(existingUid, existingEmail, imageUrl, color)
                        FireStore().registerLuggage(this@LuggageActivity, luggage)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        hideProgressDialog()
                        Toast.makeText(this@LuggageActivity, "Upload failed: ${error.description}", Toast.LENGTH_SHORT).show()
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch()
        } else {
            // Use the existing image, update with new or existing color
            val luggage = Luggage(existingUid, existingEmail, existingImage, color.ifEmpty { existingColor })
            FireStore().registerLuggage(this@LuggageActivity, luggage)
        }
    }



    fun luggageSavedSuccess() {
        hideProgressDialog()
        Toast.makeText(this, "Luggage saved successfully!", Toast.LENGTH_SHORT).show()
        finish() // Return to MainActivity
    }
}
