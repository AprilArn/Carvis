package com.aprilarn.carvis

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aprilarn.carvis.databinding.ActivitySplashScreenBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashScreenActivity : AppCompatActivity() {

    private val binding: ActivitySplashScreenBinding by lazy {

        ActivitySplashScreenBinding.inflate(layoutInflater)

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Mencegah layar mati
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // FullScreen
        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        handlerNavigate()

    }

    private fun handlerNavigate() {

        lifecycleScope.launch {
            delay(2500)
            navigateToWarning()
        }

    }

    private fun navigateToWarning() {

        startActivity(
            Intent(this, WarningScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

    }

}