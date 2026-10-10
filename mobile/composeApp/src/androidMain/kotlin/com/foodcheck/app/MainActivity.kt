package com.foodcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.foodcheck.data.network.ApiHealthMonitor
import com.foodcheck.data.network.BackendConfig
import com.foodcheck.di.sharedModule
import com.foodcheck.di.appModule
import com.foodcheck.util.rememberImagePickerLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("foodcheck", MODE_PRIVATE)
        val stored = prefs.getString("device_key", null)
        if (!stored.isNullOrBlank()) BackendConfig.deviceKey = stored
        prefs.edit().putString("device_key", BackendConfig.ensureDeviceKey()).apply()

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(applicationContext)
                modules(sharedModule, appModule)
            }
        }
        // No custom Application class in this app; MainActivity is the process entry point.
        // ApiHealthMonitor.start() is idempotent, so re-running it on activity recreation is safe.
        GlobalContext.get().get<ApiHealthMonitor>().start()

        // AppColors.Surface — status bar matches header; header content pads via platformStatusBarsPadding().
        val chrome = android.graphics.Color.parseColor("#1E293B")
        val background = android.graphics.Color.parseColor("#020617")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(chrome),
            navigationBarStyle = SystemBarStyle.dark(background),
        )
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = chrome
            window.navigationBarColor = background
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            App(imagePickerLauncher = rememberImagePickerLauncher())
        }
    }
}
