package com.viswas.taskify

import android.app.Application
import android.content.Context
import com.cloudinary.android.MediaManager
import com.viswas.taskify.utils.Constants
import java.io.File
import java.io.FileOutputStream

class TaskApplication : Application(){
    override fun onCreate() {
        super.onCreate()
        initCloudinary(this)
    }

    private fun initCloudinary(context: Context) {
        val config = mapOf(
            "cloud_name" to Constants.CLOUDINARY_NAME,
            "api_key" to Constants.API_KEY,
            "api_secret" to Constants.API_SECRET
        )
        MediaManager.init(context, config)
    }

}