package com.foodcheck.di

import com.foodcheck.presentation.state.IngredientDetailViewModel
import com.foodcheck.presentation.state.ProductDetailViewModel
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.state.SearchViewModel
import com.foodcheck.presentation.state.SubmitRequestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { SearchViewModel(get(), get()) }
    viewModel { ProductDetailViewModel(get()) }
    viewModel { IngredientDetailViewModel(get()) }
    viewModel { ScanViewModel(get(), get(), get()) }
    viewModel { com.foodcheck.presentation.state.FeedViewModel(get()) }
    viewModel { com.foodcheck.presentation.state.ShelfViewModel(get()) }
    viewModel { com.foodcheck.presentation.state.CompareViewModel(get(), get()) }
    viewModel { com.foodcheck.presentation.state.ComparisonsViewModel(get()) }
    viewModel { com.foodcheck.presentation.state.AuthViewModel(get()) }
    viewModel {
        SubmitRequestViewModel(
            submitProductRequestUseCase = get(),
            submitIngredientRequestUseCase = get(),
            unifiedSearchUseCase = get(),
        )
    }
}
