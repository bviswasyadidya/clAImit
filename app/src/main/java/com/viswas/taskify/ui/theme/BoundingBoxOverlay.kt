package com.viswas.taskify.ui.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BoundingBoxOverlay(context: Context, attrs: AttributeSet? = null) : View(context, attrs){
    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private var boxes: List<RectF> = emptyList()

    fun setBoxes(newBoxes: List<RectF>) {
        boxes = newBoxes
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) {
            canvas.drawRect(box, paint)
        }
    }

}
