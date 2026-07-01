package com.foodcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foodcheck.di.sharedModule
import com.foodcheck.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(applicationContext)
                modules(sharedModule, appModule)
            }
        }

        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
