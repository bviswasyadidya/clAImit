package com.viswas.taskify.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.viswas.taskify.R
import com.viswas.taskify.firebase.FireStore
import com.viswas.taskify.models.User
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import com.viswas.taskify.ml.LuggageDetector

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null
    private var isScanning = false
    private var lastProcessedTimestamp = 0L
    private val processingInterval = 1000L // Process every 1 second
    private lateinit var luggageDetector: LuggageDetector

    companion object {
        const val MY_PROFILE_REQUEST_CODE: Int = 11
        // Updated pattern to be more flexible with text orientation and spacing
        private val UID_PATTERN = Pattern.compile("[A-Z0-9]{7}")
    }

    private val profileActivityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                FireStore().loadUserData(this)
            } else {
                Log.e("MainActivity", "Profile update cancelled or failed!")
            }
        }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        setupActionBar()
        navView.setNavigationItemSelectedListener(this)
        FireStore().loadUserData(this)

        // Initialize the LuggageDetector
        luggageDetector = LuggageDetector(this)

        cameraExecutor = Executors.newSingleThreadExecutor()
        previewView = findViewById(R.id.preview_view)
        previewView.visibility = View.GONE // Hide until scanning starts

        findViewById<FloatingActionButton>(R.id.fab_scan_luggage).setOnClickListener {
            requestCameraPermission()
        }
    }

    private fun setupActionBar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_main_activity)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_action_navigation_menu)
        toolbar.setNavigationOnClickListener {
            toggleDrawer()
        }
    }

    private fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Only use ML Kit for text recognition
            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        // Only process frames at the specified interval to avoid overwhelming the system
                        if (currentTime - lastProcessedTimestamp >= processingInterval) {
                            lastProcessedTimestamp = currentTime
                            processImageWithTFLite(imageProxy, textRecognizer)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                previewView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera binding failed", e)
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageWithTFLite(
        imageProxy: ImageProxy,
        textRecognizer: com.google.mlkit.vision.text.TextRecognizer
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                // Create InputImage for text recognition
                val image = InputImage.fromBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                
                // Perform luggage detection with TFLite
                val detectedLuggage = luggageDetector.detectLuggage(bitmap)
                
                // Log luggage detection results
                Log.d("MainActivity", "LUGGAGE DETECTION: Detected ${detectedLuggage.size} luggage items")
                
                // Log details of each detected luggage item
                detectedLuggage.forEachIndexed { index, luggage ->
                    Log.d("MainActivity", "LUGGAGE DETECTION: Item $index - Type: ${luggage.type}, Bounds: ${luggage.boundingBox}, Confidence: ${luggage.confidence}")
                }
                
                // Now proceed with text recognition using ML Kit
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        Log.d("MainActivity", "TEXT RECOGNITION: Found text: ${visionText.text}")
                        var uidFound = false
                        
                        val fullTextMatcher = UID_PATTERN.matcher(visionText.text.replace("\\s+".toRegex(), ""))
                        if (fullTextMatcher.find()) {
                            val detectedUid = fullTextMatcher.group()
                            Log.d("MainActivity", "TEXT RECOGNITION: Found UID in full text: $detectedUid")
                            verifyLuggageAndShowResult(detectedUid, bitmap)
                            uidFound = true
                        } else {
                            for (block in visionText.textBlocks) {
                                Log.d("MainActivity", "TEXT RECOGNITION: Text block: ${block.text}")
                                
                                val blockText = block.text.replace("\\s+".toRegex(), "")
                                val blockMatcher = UID_PATTERN.matcher(blockText)
                                if (blockMatcher.find()) {
                                    val detectedUid = blockMatcher.group()
                                    Log.d("MainActivity", "TEXT RECOGNITION: Found UID in block: $detectedUid")
                                    verifyLuggageAndShowResult(detectedUid, bitmap)
                                    uidFound = true
                                    break
                                }
                                
                                for (line in block.lines) {
                                    Log.d("MainActivity", "TEXT RECOGNITION: Line: ${line.text}")
                                    val lineText = line.text.replace("\\s+".toRegex(), "")
                                    val matcher = UID_PATTERN.matcher(lineText)
                                    if (matcher.find()) {
                                        val detectedUid = matcher.group()
                                        Log.d("MainActivity", "TEXT RECOGNITION: Found UID in line: $detectedUid")
                                        verifyLuggageAndShowResult(detectedUid, bitmap)
                                        uidFound = true
                                        break
                                    }
                                    
                                    for (element in line.elements) {
                                        val elementText = element.text.replace("\\s+".toRegex(), "")
                                        if (elementText.length >= 7) {
                                            val elementMatcher = UID_PATTERN.matcher(elementText)
                                            if (elementMatcher.find()) {
                                                val detectedUid = elementMatcher.group()
                                                Log.d("MainActivity", "TEXT RECOGNITION: Found UID in element: $detectedUid")
                                                verifyLuggageAndShowResult(detectedUid, bitmap)
                                                uidFound = true
                                                break
                                            }
                                        }
                                    }
                                    if (uidFound) break
                                }
                                if (uidFound) break
                            }
                        }
                        
                        // If no UID found in text, try to recognize text in each luggage's bounding box
                        if (!uidFound && detectedLuggage.isNotEmpty()) {
                            var itemsProcessed = 0
                            
                            for (luggage in detectedLuggage) {
                                try {
                                    val boundingBox = luggage.boundingBox
                                    if (boundingBox.width() > 0 && boundingBox.height() > 0 &&
                                        boundingBox.left >= 0 && boundingBox.top >= 0 &&
                                        boundingBox.right <= bitmap.width && boundingBox.bottom <= bitmap.height
                                    ) {
                                        val croppedBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            boundingBox.left.toInt(),
                                            boundingBox.top.toInt(),
                                            boundingBox.width().toInt(),
                                            boundingBox.height().toInt()
                                        )
                                        
                                        // Process the cropped image for text
                                        val croppedImage = InputImage.fromBitmap(croppedBitmap, 0)
                                        textRecognizer.process(croppedImage)
                                            .addOnSuccessListener { croppedVisionText ->
                                                val croppedTextMatcher = UID_PATTERN.matcher(
                                                    croppedVisionText.text.replace("\\s+".toRegex(), "")
                                                )
                                                if (croppedTextMatcher.find()) {
                                                    val detectedUid = croppedTextMatcher.group()
                                                    Log.d("MainActivity", "TEXT RECOGNITION: Found UID in ${luggage.type}: $detectedUid")
                                                    verifyLuggageAndShowResult(detectedUid, bitmap)
                                                }
                                                
                                                itemsProcessed++
                                                if (itemsProcessed >= detectedLuggage.size) {
                                                    imageProxy.close()
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("MainActivity", "Cropped text recognition failed", e)
                                                itemsProcessed++
                                                if (itemsProcessed >= detectedLuggage.size) {
                                                    imageProxy.close()
                                                }
                                            }
                                    } else {
                                        itemsProcessed++
                                        if (itemsProcessed >= detectedLuggage.size) {
                                            imageProxy.close()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error processing cropped bitmap", e)
                                    itemsProcessed++
                                    if (itemsProcessed >= detectedLuggage.size) {
                                        imageProxy.close()
                                    }
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "TEXT RECOGNITION: Text recognition failed", e)
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        // Convert the mediaImage to NV21 byte array using our helper
        val nv21 = yuv420ToNv21(mediaImage)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer.duplicate().apply { rewind() }
        val uBuffer = uPlane.buffer.duplicate().apply { rewind() }
        val vBuffer = vPlane.buffer.duplicate().apply { rewind() }

        // NV21: Y plane size + UV plane size (interleaved VU)
        val ySize = width * height
        val nv21 = ByteArray(ySize + width * height / 2)

        // Copy Y data row by row, accounting for row stride
        var pos = 0
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            yBuffer.position(rowStart)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // Process U and V planes. They have dimensions width/2 x height/2.
        // NV21 expects interleaved V and U values (V first then U).
        for (row in 0 until height / 2) {
            val uRowStart = row * uPlane.rowStride
            val vRowStart = row * vPlane.rowStride
            for (col in 0 until width / 2) {
                uBuffer.position(uRowStart + col * uPlane.pixelStride)
                vBuffer.position(vRowStart + col * vPlane.pixelStride)
                nv21[pos++] = vBuffer.get()
                nv21[pos++] = uBuffer.get()
            }
        }
        return nv21
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String? {
        return try {
            val file = File(cacheDir, "detected_image_${System.currentTimeMillis()}.jpg")
            val outputStream = file.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving bitmap to cache", e)
            null
        }
    }

    private fun verifyLuggageAndShowResult(detectedUid: String, detectedBitmap: Bitmap?) {
        if (!isScanning) {  // Prevent multiple simultaneous verifications
            isScanning = true
            FireStore().getLuggageByUid(detectedUid) { luggage ->
                if (luggage != null) {
                    // Get user details from the email
                    FireStore().getUserByEmail(luggage.email) { user ->
                        if (user != null) {
                            runOnUiThread {
                                // Stop scanning
                                cameraProvider?.unbindAll()
                                previewView.visibility = View.GONE

                                // Save the detected bitmap to cache and get the file path
                                val imagePath = detectedBitmap?.let { saveBitmapToCache(it) }

                                // Launch ResultActivity with added extra for object image
                                val intent = Intent(this, ResultActivity::class.java).apply {
                                    putExtra("USER_NAME", user.name)
                                    putExtra("LUGGAGE_UID", detectedUid)
                                    putExtra("OBJECT_IMAGE_PATH", imagePath)
                                }
                                startActivity(intent)
                            }
                        } else {
                            isScanning = false  // Reset if user not found
                        }
                    }
                } else {
                    isScanning = false  // Reset if luggage not found
                }
            }
        }
    }

    fun updateNavigationUserDetails(user: User) {
        val ivUserImage: CircleImageView = findViewById(R.id.iv_user_image)
        val tvUsername = findViewById<TextView>(R.id.tv_username)
        Glide
            .with(this)
            .load(user.image)
            .fitCenter()
            .placeholder(R.drawable.ic_user_place_holder)
            .into(ivUserImage)
        tvUsername.text = user.name
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            doubleBackToExit()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_my_profile -> {
                val intent = Intent(this, ProfileActivity::class.java)
                profileActivityLauncher.launch(intent)
            }
            R.id.nav_luggage -> {
                startActivity(Intent(this, LuggageActivity::class.java))
            }
            R.id.nav_sign_out -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, IntroActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        luggageDetector.close()
    }
}