package com.foodcheck.presentation.theme

enum class Platform {
    ANDROID,
    IOS
}

expect val currentPlatform: Platform
