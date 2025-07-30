package com.lauraharoescoi.keeporsweep

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val VIDEO_URI = "video_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val videoView = findViewById<VideoView>(R.id.video_view)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val videoUriString = intent.getStringExtra(VIDEO_URI)

        if (videoUriString != null) {
            val videoUri = Uri.parse(videoUriString)
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)
            videoView.setVideoURI(videoUri)

            progressBar.visibility = View.VISIBLE
            videoView.setOnPreparedListener { mediaPlayer ->
                progressBar.visibility = View.GONE
                mediaPlayer.start()
            }

            videoView.setOnErrorListener { _, _, _ ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show()
                finish() // Close the player on error
                true
            }
        } else {
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
