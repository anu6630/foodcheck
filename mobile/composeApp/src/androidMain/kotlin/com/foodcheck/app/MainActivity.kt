package com.foodcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import biz.lifex.appupdate.AppUpdateClient
import biz.lifex.appupdate.AppUpdateController
import biz.lifex.appupdate.ForceUpdateScreen
import biz.lifex.appupdate.UpdatePolicy
import biz.lifex.foodcheck.BuildConfig
import com.foodcheck.ads.AdsController
import com.foodcheck.app.update.AppUpdateGate
import com.foodcheck.data.local.AppFilesBootstrap
import com.foodcheck.data.network.ApiHealthMonitor
import com.foodcheck.data.network.BackendConfig
import com.foodcheck.di.sharedModule
import com.foodcheck.di.appModule
import com.foodcheck.util.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File

class MainActivity : ComponentActivity() {

    /** Must be created before STARTED so the Play update ActivityResult is registered. */
    private val appUpdateController = AppUpdateController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdsController.attach(this)
        com.foodcheck.billing.initAndroidBillingManager(this).startBillingConnection()

        val prefs = getSharedPreferences("foodcheck", MODE_PRIVATE)
        BackendConfig.onDeviceKeyChanged = { key ->
            prefs.edit().putString("device_key", key).apply()
        }
        val stored = prefs.getString("device_key", null)
        if (!stored.isNullOrBlank()) BackendConfig.deviceKey = stored
        prefs.edit().putString("device_key", BackendConfig.ensureDeviceKey()).apply()
        AppFilesBootstrap.init(File(filesDir, "cache").absolutePath)

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
            var forcePolicy by remember { mutableStateOf<UpdatePolicy?>(null) }

            LaunchedEffect(Unit) {
                val policy = AppUpdateClient(AppUpdateGate.BASE_URL, AppUpdateGate.APP_KEY)
                    .fetch(BuildConfig.VERSION_CODE)
                    .getOrNull() ?: return@LaunchedEffect // fail-open
                appUpdateController.apply(
                    policy = policy,
                    onForceBlocked = { forcePolicy = it },
                )
            }

            val blocked = forcePolicy
            if (blocked != null) {
                ForceUpdateScreen(
                    policy = blocked,
                    onUpdateClick = {
                        AppUpdateController.openStore(this@MainActivity, blocked)
                    },
                )
            } else {
                App(imagePickerLauncher = rememberImagePickerLauncher())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            appUpdateController.resumeIfInProgress()
        }
    }
}
