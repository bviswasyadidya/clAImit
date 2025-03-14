package com.viswas.taskify.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Luggage(
    val uid:String = "",
    val email:String = "",
    val image:String = "",
    val color:String = ""
):Parcelable
