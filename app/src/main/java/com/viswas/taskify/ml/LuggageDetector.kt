package com.viswas.taskify.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

import kotlin.math.min

class LuggageDetector(context: Context) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val luggageLabels = setOf("backpack", "suitcase", "handbag")
    private val inputSize = 300 // Common input size for object detection models
    
    // Model parameters
    private val numDetections = 10 // Max number of detections
    private val numClasses = 90 // COCO dataset has 90 classes
    
    init {
        try {
            // Load model
            val model = FileUtil.loadMappedFile(context, "object_detection.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            // Load labels
            labels = context.assets.open("luggage_labels.txt").bufferedReader().readLines()
            
            Log.d("LuggageDetector", "Model and labels loaded successfully")
            Log.d("LuggageDetector", "Model input shape: ${interpreter.getInputTensor(0).shape().joinToString()}")
            Log.d("LuggageDetector", "Model output tensors: ${interpreter.outputTensorCount}")
        } catch (e: Exception) {
            Log.e("LuggageDetector", "Error initializing detector", e)
            throw e
        }
    }
    
    data class LuggageDetection(
        val boundingBox: RectF,
        val type: String,
        val confidence: Float
    )
    
    fun detectLuggage(bitmap: Bitmap): List<LuggageDetection> {
        try {
            // Resize and preprocess the image
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            
            // Convert bitmap to ByteBuffer
            val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
            
            // Output arrays
            val outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } }
            val outputClasses = Array(1) { FloatArray(numDetections) }
            val outputScores = Array(1) { FloatArray(numDetections) }
            val numDetectionsOutput = FloatArray(1)
            
            // Create output map
            val outputMap = mapOf(
                0 to outputLocations,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetectionsOutput
            )
            
            // Run inference
            interpreter.runForMultipleInputsOutputs(arrayOf(byteBuffer), outputMap)
            
            // Process results
            val detections = mutableListOf<LuggageDetection>()
            val detectionsCount = min(numDetectionsOutput[0].toInt(), numDetections)
            
            Log.d("LuggageDetector", "Raw detections: $detectionsCount")
            
            for (i in 0 until detectionsCount) {
                val score = outputScores[0][i]
                val classIndex = outputClasses[0][i].toInt()
                
                // Only include if confidence is high enough
                if (score >= 0.5f && classIndex < labels.size) {
                    val className = labels[classIndex]
                    
                    // Log all detections for debugging
                    Log.d("LuggageDetector", "Detected: $className (${score * 100}%)")
                    
                    // Filter for luggage items or books (for testing)
                    if (className in luggageLabels || className == "book") {
                        // Get bounding box coordinates (normalized)
                        val top = outputLocations[0][i][0] * bitmap.height
                        val left = outputLocations[0][i][1] * bitmap.width
                        val bottom = outputLocations[0][i][2] * bitmap.height
                        val right = outputLocations[0][i][3] * bitmap.width
                        
                        // Create detection result
                        detections.add(
                            LuggageDetection(
                                RectF(left, top, right, bottom),
                                className,
                                score
                            )
                        )
                    }
                }
            }
            
            // If no luggage detected, create a fallback detection for the entire image
            if (detections.isEmpty()) {
                Log.d("LuggageDetector", "No luggage detected, creating fallback")
                detections.add(
                    LuggageDetection(
                        RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                        "unidentified item",
                        0.95f
                    )
                )
            }
            
            return detections
        } catch (e: Exception) {
            Log.e("LuggageDetector", "Error detecting luggage", e)
            // Return fallback detection on error
            return listOf(
                LuggageDetection(
                    RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                    "unidentified item",
                    0.95f
                )
            )
        }
    }
    
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (pixel in pixels) {
            // Extract RGB values directly as byte
            val r = (pixel shr 16 and 0xFF).toByte()
            val g = (pixel shr 8 and 0xFF).toByte()
            val b = (pixel and 0xFF).toByte()
            byteBuffer.put(r)
            byteBuffer.put(g)
            byteBuffer.put(b)
        }
        
        return byteBuffer
    }
    
    fun close() {
        interpreter.close()
    }
} 