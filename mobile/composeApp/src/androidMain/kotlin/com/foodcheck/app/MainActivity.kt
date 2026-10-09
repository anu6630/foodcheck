package com.foodcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.foodcheck.data.network.ApiHealthMonitor
import com.foodcheck.di.sharedModule
import com.foodcheck.di.appModule
import com.foodcheck.util.rememberImagePickerLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(applicationContext)
                modules(sharedModule, appModule)
            }
        }
        // No custom Application class in this app; MainActivity is the process entry point.
        // ApiHealthMonitor.start() is idempotent, so re-running it on activity recreation is safe.
        GlobalContext.get().get<ApiHealthMonitor>().start()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            App(imagePickerLauncher = rememberImagePickerLauncher())
        }
    }
}
