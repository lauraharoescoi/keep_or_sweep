package com.lauraharoescoi.keeporsweep

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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class Photo(val uri: Uri)

class PhotosFragment : Fragment() {

    private lateinit var cardContainer: FrameLayout
    private val photoList = mutableListOf<Photo>()
    private var currentPhotoIndex = 0
    private var dX = 0f
    private var dY = 0f

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize launchers
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadPhotos() else Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_LONG).show()
        }
        deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val message = if (result.resultCode == Activity.RESULT_OK) "Photo deleted" else "Deletion cancelled"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            currentPhotoIndex++
            showNextCard()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_photos, container, false)
        cardContainer = view.findViewById(R.id.card_container)

        // Setup button listeners
        val keepButton = view.findViewById<ImageButton>(R.id.keep_button)
        val sweepButton = view.findViewById<ImageButton>(R.id.sweep_button)

        keepButton.setOnClickListener { triggerSwipe(isKeep = true) }
        sweepButton.setOnClickListener { triggerSwipe(isKeep = false) }

        checkPermissionAndLoadPhotos()
        return view
    }

    private fun triggerSwipe(isKeep: Boolean) {
        if (cardContainer.childCount > 0) {
            val topCard = cardContainer.getChildAt(cardContainer.childCount - 1)
            handleSwipe(topCard, isKeep)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchListenerToCard(cardView: View) {
        val keepOverlay = cardView.findViewById<TextView>(R.id.keep_overlay)
        val sweepOverlay = cardView.findViewById<TextView>(R.id.sweep_overlay)

        cardView.setOnTouchListener { view, event ->
            val screenWidth = resources.displayMetrics.widthPixels
            val swipeThreshold = screenWidth / 4

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    view.x = newX
                    view.y = event.rawY + dY

                    // Update overlay alpha based on swipe distance
                    val swipeProgress = (newX / swipeThreshold).coerceIn(-1f, 1f)
                    keepOverlay.alpha = if (swipeProgress > 0) swipeProgress else 0f
                    sweepOverlay.alpha = if (swipeProgress < 0) -swipeProgress else 0f
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    keepOverlay.alpha = 0f
                    sweepOverlay.alpha = 0f
                    when {
                        view.x > swipeThreshold -> handleSwipe(view, isKeep = true)
                        view.x < -swipeThreshold -> handleSwipe(view, isKeep = false)
                        else -> view.animate().x(0f).y(0f).setDuration(200).start()
                    }
                }
            }
            true
        }
    }

    private fun handleSwipe(cardView: View, isKeep: Boolean) {
        cardView.setOnTouchListener(null)
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val endX = if (isKeep) screenWidth * 1.5f else -screenWidth * 1.5f

        cardView.animate().x(endX).alpha(0f).setDuration(300).withEndAction {
            cardContainer.removeView(cardView)
            if (isKeep) {
                currentPhotoIndex++
                showNextCard()
            } else {
                deletePhotoWithConfirmation(photoList[currentPhotoIndex])
            }
        }.start()
    }

    // All other functions (loadPhotos, showNextCard, deletePhotoWithConfirmation, etc.) remain the same
    private fun checkPermissionAndLoadPhotos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                loadPhotos()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadPhotos() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            photoList.clear()

            requireActivity().contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
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
                    Toast.makeText(requireContext(), "No photos found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNextCard() {
        cardContainer.removeAllViews()
        if (currentPhotoIndex >= photoList.size) {
            Toast.makeText(requireContext(), "All photos processed!", Toast.LENGTH_SHORT).show()
            return
        }

        val cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_photo_card, cardContainer, false)
        val imageView = cardView.findViewById<ImageView>(R.id.photo_image_view)
        val photo = photoList[currentPhotoIndex]

        Glide.with(this).load(photo.uri).into(imageView)
        cardContainer.addView(cardView)
        addTouchListenerToCard(cardView)
    }

    private fun deletePhotoWithConfirmation(photo: Photo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intentSender = MediaStore.createDeleteRequest(requireActivity().contentResolver, listOf(photo.uri)).intentSender
            val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
            deleteRequestLauncher.launch(intentSenderRequest)
        } else {
            Toast.makeText(requireContext(), "Auto-delete only works on Android 11+", Toast.LENGTH_LONG).show()
            currentPhotoIndex++
            showNextCard()
        }
    }
}