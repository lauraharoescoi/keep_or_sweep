package com.lauraharoescoi.keeporsweep // Make sure this is your package name

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Photo(val uri: Uri)

class MainActivity : AppCompatActivity() {

    private lateinit var cardContainer: FrameLayout
    private val photoList = mutableListOf<Photo>()
    private var currentPhotoIndex = 0

    private var dX = 0f
    private var dY = 0f

    // This launcher handles the result from the system's delete confirmation dialog.
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                loadPhotos()
            } else {
                Toast.makeText(this, "Permission denied. App cannot function.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardContainer = findViewById(R.id.card_container)

        // Initialize the launcher to handle the user's response (confirm or cancel).
        deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Deletion cancelled", Toast.LENGTH_SHORT).show()
            }
            // Whether the photo was deleted or not, we move to the next one.
            currentPhotoIndex++
            showNextCard()
        }

        checkPermissionAndLoadPhotos()
    }

    private fun checkPermissionAndLoadPhotos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadPhotos()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadPhotos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    photoList.add(Photo(contentUri))
                }
            }

            withContext(Dispatchers.Main) {
                if (photoList.isNotEmpty()) {
                    showNextCard()
                } else {
                    Toast.makeText(this@MainActivity, "No photos found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNextCard() {
        cardContainer.removeAllViews()

        if (currentPhotoIndex >= photoList.size) {
            Toast.makeText(this, "All photos processed!", Toast.LENGTH_SHORT).show()
            return
        }

        val cardView = LayoutInflater.from(this).inflate(R.layout.item_photo_card, cardContainer, false)
        val imageView = cardView.findViewById<ImageView>(R.id.photo_image_view)
        val photo = photoList[currentPhotoIndex]

        Glide.with(this).load(photo.uri).into(imageView)
        cardContainer.addView(cardView)
        addTouchListenerToCard(cardView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchListenerToCard(cardView: View) {
        cardView.setOnTouchListener { view, event ->
            val screenWidth = resources.displayMetrics.widthPixels
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
                }
                MotionEvent.ACTION_UP -> {
                    val finalX = view.x
                    val swipeThreshold = screenWidth / 3
                    when {
                        finalX > swipeThreshold -> handleSwipe(cardView, isKeep = true)
                        finalX < -swipeThreshold -> handleSwipe(cardView, isKeep = false)
                        else -> view.animate().x(0f).y(0f).setDuration(300).start()
                    }
                }
            }
            true
        }
    }

    private fun handleSwipe(cardView: View, isKeep: Boolean) {
        cardView.setOnTouchListener(null)

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val endX = if (isKeep) screenWidth else -screenWidth

        cardView.animate().x(endX).alpha(0f).setDuration(300).withEndAction {
            if (isKeep) {
                Toast.makeText(this, "Kept", Toast.LENGTH_SHORT).show()
                currentPhotoIndex++
                showNextCard()
            } else {
                // For a 'sweep', we trigger the deletion request.
                // The logic will continue in the deleteRequestLauncher's callback.
                deletePhotoWithConfirmation(photoList[currentPhotoIndex])
            }
        }.start()
    }

    private fun deletePhotoWithConfirmation(photo: Photo) {
        // This is the modern, safe way to request a file deletion.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 and higher
            val intentSender = MediaStore.createDeleteRequest(contentResolver, listOf(photo.uri)).intentSender
            val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
            deleteRequestLauncher.launch(intentSenderRequest)
        } else {
            // For older versions, this safe method doesn't exist.
            Toast.makeText(this, "Auto-delete only works on Android 11+", Toast.LENGTH_LONG).show()
            currentPhotoIndex++
            showNextCard() // We skip the file to not get stuck.
        }
    }
}