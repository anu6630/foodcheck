package com.foodcheck.di

import org.koin.dsl.module
import com.foodcheck.presentation.state.MainViewModel

val appModule = module {
    factory { MainViewModel(get(), get(), get()) }
}
