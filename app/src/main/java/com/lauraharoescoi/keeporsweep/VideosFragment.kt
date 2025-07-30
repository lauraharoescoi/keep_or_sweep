package com.lauraharoescoi.keeporsweep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
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

data class Video(val uri: Uri)

class VideosFragment : Fragment() {

    private lateinit var cardContainer: FrameLayout
    private val videoList = mutableListOf<Video>()
    private var currentVideoIndex = 0

    // Variables para el gesto de arrastrar
    private var dX = 0f
    private var dY = 0f
    private var startX = 0f
    private var startY = 0f

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadVideos() else Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_LONG).show()
        }
        deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val message = if (result.resultCode == Activity.RESULT_OK) "Video deleted" else "Deletion cancelled"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            currentVideoIndex++
            showNextCard()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_photos, container, false)
        cardContainer = view.findViewById(R.id.card_container)

        val keepButton = view.findViewById<ImageButton>(R.id.keep_button)
        val sweepButton = view.findViewById<ImageButton>(R.id.sweep_button)

        keepButton.setOnClickListener { triggerSwipe(isKeep = true) }
        sweepButton.setOnClickListener { triggerSwipe(isKeep = false) }

        checkPermissionAndLoadVideos()
        return view
    }

    private fun checkPermissionAndLoadVideos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadVideos() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            videoList.clear()
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC"
            requireActivity().contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    videoList.add(Video(contentUri))
                }
            }
            withContext(Dispatchers.Main) {
                if (videoList.isNotEmpty()) showNextCard() else Toast.makeText(requireContext(), "No videos found.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showNextCard() {
        cardContainer.removeAllViews()
        if (currentVideoIndex >= videoList.size) {
            Toast.makeText(requireContext(), "All videos processed!", Toast.LENGTH_SHORT).show()
            return
        }
        val cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_video_card, cardContainer, false)
        val thumbnailView = cardView.findViewById<ImageView>(R.id.video_thumbnail_view)
        val video = videoList[currentVideoIndex]
        Glide.with(this).load(video.uri).into(thumbnailView)
        cardContainer.addView(cardView)
        addTouchListenerToCard(cardView)
    }

    private fun triggerSwipe(isKeep: Boolean) {
        if (cardContainer.childCount > 0) {
            handleSwipe(cardContainer.getChildAt(cardContainer.childCount - 1), isKeep)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchListenerToCard(cardView: View) {
        val keepOverlay = cardView.findViewById<TextView>(R.id.keep_overlay)
        val sweepOverlay = cardView.findViewById<TextView>(R.id.sweep_overlay)

        cardView.setOnTouchListener { view, event ->
            val swipeThreshold = resources.displayMetrics.widthPixels / 4
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = event.rawX + dX
                    view.y = event.rawY + dY
                    val swipeProgress = (view.x / swipeThreshold).coerceIn(-1f, 1f)
                    keepOverlay.alpha = if (swipeProgress > 0) swipeProgress else 0f
                    sweepOverlay.alpha = if (swipeProgress < 0) -swipeProgress else 0f
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    keepOverlay.alpha = 0f
                    sweepOverlay.alpha = 0f

                    if (isAClick(startX, event.rawX, startY, event.rawY)) {
                        playVideo(videoList[currentVideoIndex])
                        view.animate().x(0f).y(0f).setDuration(200).start()
                    } else {
                        when {
                            view.x > swipeThreshold -> handleSwipe(view, isKeep = true)
                            view.x < -swipeThreshold -> handleSwipe(view, isKeep = false)
                            else -> view.animate().x(0f).y(0f).setDuration(200).start()
                        }
                    }
                }
            }
            true
        }
    }

    private fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float): Boolean {
        val differenceX = abs(startX - endX)
        val differenceY = abs(startY - endY)
        return differenceX < 10 && differenceY < 10 // Click threshold
    }

    private fun playVideo(video: Video) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.VIDEO_URI, video.uri.toString())
        }
        startActivity(intent)
    }

    private fun handleSwipe(cardView: View, isKeep: Boolean) {
        cardView.setOnTouchListener(null)
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val endX = if (isKeep) screenWidth * 1.5f else -screenWidth * 1.5f
        cardView.animate().x(endX).alpha(0f).setDuration(300).withEndAction {
            cardContainer.removeView(cardView)
            if (isKeep) {
                Toast.makeText(requireContext(), "Kept", Toast.LENGTH_SHORT).show()
                currentVideoIndex++
                showNextCard()
            } else {
                deleteVideoWithConfirmation(videoList[currentVideoIndex])
            }
        }.start()
    }

    private fun deleteVideoWithConfirmation(video: Video) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intentSender = MediaStore.createDeleteRequest(requireActivity().contentResolver, listOf(video.uri)).intentSender
            val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
            deleteRequestLauncher.launch(intentSenderRequest)
        } else {
            Toast.makeText(requireContext(), "Auto-delete only works on Android 11+", Toast.LENGTH_LONG).show()
            currentVideoIndex++
            showNextCard()
        }
    }
}