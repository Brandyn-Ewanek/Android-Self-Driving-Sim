package com.example.selfdrivingsim

// --- Imports ---
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    // --- Class Variables ---
    private lateinit var textureView: TextureView
    private lateinit var steeringWheel: ImageView
    private lateinit var interveneButton: Button
    private lateinit var interventionCountText: TextView
    private var tflite: Interpreter? = null
    private var mediaPlayer: MediaPlayer? = null
    private var interventionCount = 0

    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    private val inferenceRunnable = object : Runnable {
        override fun run() {
            // Check if the video is playing and the surface is ready
            if (mediaPlayer?.isPlaying == true && textureView.isAvailable) {
                // Get the bitmap on the main thread context, but immediately use it here
                val currentFrameBitmap = textureView.bitmap // Safe to call on background thread as it requests from the main thread

                if (currentFrameBitmap != null && tflite != null) {
                    try {
                        val inputBuffer = convertBitmapToByteBuffer(currentFrameBitmap)
                        val steeringAngle = runInference(inputBuffer)

                        // Post the UI update back to the main thread
                        steeringWheel.post {
                            steeringWheel.rotation = steeringAngle
                        }
                    } catch (e: Exception) {
                        println("Error during frame processing: ${e.message}")
                    }
                }
            }
            // Schedule the next inference run (e.g., every 100ms for 10 FPS)
            backgroundHandler.postDelayed(this, 100)
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            // Start with a default video when the app first launches
            playVideo(surface, "daytime_1.mp4")
            println("Surface Texture Available. Called initial playVideo.")
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            // Not needed
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            // 🎯 Stop the runnable when the surface is destroyed
            backgroundHandler.removeCallbacks(inferenceRunnable)

            println("Surface Texture Destroyed. MediaPlayer released.")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // This is empty, which is correct
        }


    }


    // --- onCreate Method ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        steeringWheel = findViewById(R.id.steeringWheel)
        interveneButton = findViewById(R.id.interveneButton)
        interventionCountText = findViewById(R.id.interventionCountText)
        interventionCountText.text = "Intervention Count: 0"



        interveneButton.setOnClickListener {
            interventionCount++
            interventionCountText.text = "Intervention Count: $interventionCount"
        }

        findViewById<Button>(R.id.btnDay1).setOnClickListener {
            changeVideo("daytime_1.mp4")
        }
        findViewById<Button>(R.id.btnDay2).setOnClickListener {
            changeVideo("daytime_2.mp4")
        }
        findViewById<Button>(R.id.btnNight1).setOnClickListener {
            changeVideo("nighttime_1.mp4")
        }
        findViewById<Button>(R.id.btnNight2).setOnClickListener {
            changeVideo("nighttime_2.mp4")
        }

        textureView.surfaceTextureListener = surfaceTextureListener

        backgroundThread = HandlerThread("InferenceThread")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)


        try {
            tflite = Interpreter(loadModelFile())
            println(">>> SIMPLE TFLITE MODEL LOADED SUCCESSFULLY <<<")
        } catch (e: Exception) {
            println(">>> Error loading simple TFLite model: ${e.message}")
            e.printStackTrace()
        }

    } // End of onCreate

    private fun changeVideo(videoFileName: String) {
        if (textureView.isAvailable) {
            // If the surface is ready, play the new video
            playVideo(textureView.surfaceTexture!!, videoFileName)
        } else {
            // This is a fallback, but generally the surface should be ready
            // after the app has loaded.
            println("Surface not available yet. Can't change video.")
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = this.assets.openFd("final_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        inputStream.close()
        return mappedByteBuffer
    }

    private fun playVideo(surfaceTexture: SurfaceTexture, videoFileName: String = "daytime_1.mp4") {
        // Stop any previous media and inference loop before starting a new one
        backgroundHandler.removeCallbacks(inferenceRunnable)
        mediaPlayer?.release()
        mediaPlayer = null

        val surface = Surface(surfaceTexture)

        // 🔥 REMOVE THE OLD surfaceTexture.setOnFrameAvailableListener {} BLOCK ENTIRELY 🔥

        try {
            val afd: AssetFileDescriptor = assets.openFd(videoFileName)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setSurface(surface)

                setOnPreparedListener { player ->
                    println("MediaPlayer prepared. Starting video: $videoFileName")
                    player.start()
                    afd.close()

                    // 🎯 START the periodic inference loop now that the video is playing
                    backgroundHandler.post(inferenceRunnable)
                }

                setOnCompletionListener { player ->
                    println("Video completed. Restarting.")
                    player.seekTo(0)
                    player.start()
                }

                setOnErrorListener { _, what, extra ->
                    println("MediaPlayer Error: what=$what, extra=$extra")

                    true
                }

                prepareAsync()

            }

        } catch (e: Exception) {
            println("Error preparing or playing video '$videoFileName': ${e.message}")
            e.printStackTrace()
        }
    }

    // --- THIS FUNCTION IS NOW FIXED ---
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Model Input Dimensions (Must match your .tflite model)
        val modelInputWidth = 320
        val modelInputHeight = 160
        val modelInputChannels = 3
        val batchSize = 1
        val bytesPerChannel = 4 // Float32

        val byteBuffer = ByteBuffer.allocateDirect(batchSize * modelInputHeight * modelInputWidth * modelInputChannels * bytesPerChannel)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()

        // --- Bitmap Preprocessing ---
        // 1. Resize the input bitmap to the exact size the model's input layer expects
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, true)

        // 2. Get pixel data for the *full resized image*
        val intValues = IntArray(modelInputWidth * modelInputHeight)
        resizedBitmap.getPixels(intValues, 0, modelInputWidth, 0, 0, modelInputWidth, modelInputHeight)

        // 3. Normalize and put into ByteBuffer
        var pixel = 0
        for (y in 0 until modelInputHeight) { // Iterate over FULL height
            for (x in 0 until modelInputWidth) { // Iterate over FULL width
                val value = intValues[pixel++]
                // Extract RGB and normalize
                val r = (((value shr 16) and 0xFF) / 255.0f) - 0.5f
                val g = (((value shr 8) and 0xFF) / 255.0f) - 0.5f
                val b = ((value and 0xFF) / 255.0f) - 0.5f
                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }

        resizedBitmap.recycle() // We can recycle this temporary bitmap
        byteBuffer.rewind() // Rewind buffer before returning
        return byteBuffer
    }

    private fun runInference(byteBuffer: ByteBuffer): Float {
        if (tflite == null) {
            println("TFLite interpreter is null.")
            return 0.0f
        }

        val outputArray = Array(1) { FloatArray(1) }

        try {
            tflite?.run(byteBuffer, outputArray)
        } catch (e: Exception) {
            println("Error running TFLite inference: ${e.message}")
            e.printStackTrace()
            return 0.0f
        }

        val predictedAngle = outputArray[0][0]
        val displayAngle = predictedAngle * 90.0f // Scaling factor

        return displayAngle.coerceIn(-90f, 90f) // Limit angle for display
    }


    override fun onDestroy() {
        super.onDestroy() // Always call the superclass method first

        // STOP THE BACKGROUND THREAD SAFELY
        backgroundThread.quitSafely()
        try {
            backgroundThread.join() // Wait for the thread to finish
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // RELEASE TFLITE INTERPRETER
        tflite?.close()
        tflite = null

        // RELEASE MEDIA PLAYER
        mediaPlayer?.release()
        mediaPlayer = null

        println("MainActivity onDestroy called. All resources released.")
    }

} // --- End of MainActivity class ---