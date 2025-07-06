package com.aprilarn.carvis

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aprilarn.carvis.databinding.ActivityWarningScreenBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WarningScreenActivity : AppCompatActivity() {

    private val binding: ActivityWarningScreenBinding by lazy {

        ActivityWarningScreenBinding.inflate(layoutInflater)

    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//
//        // Mencegah layar mati
//        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        // FullScreen
//        window.decorView.apply {
//            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
//        }
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(binding.root)
//        handlerNavigate()
//
//    }

    // SplashScreenActivity.kt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Panggil enableEdgeToEdge() di sini, setelah super.onCreate()
        enableEdgeToEdge()

        // Mencegah layar mati
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Panggil setContentView() setelah enableEdgeToEdge()
        setContentView(binding.root)

        // 3. Terapkan kode fullscreen modern setelah setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        handlerNavigate()
    }

    private fun handlerNavigate() {

        lifecycleScope.launch {
            delay(5500)
            navigateToMain()
        }

    }

    private fun navigateToMain() {

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

    }

}