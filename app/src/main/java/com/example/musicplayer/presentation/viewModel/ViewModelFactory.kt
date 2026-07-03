package com.example.musicplayer.presentation.viewModel

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.musicplayer.data.AudioRepositoryImpl
import com.example.musicplayer.domain.GetAudioTracksUseCase

@ExperimentalMaterial3Api
fun viewModelFactory(
    context: Context,
    store: ViewModelStoreOwner,
): AudioViewModel {


    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioViewModel(
                GetAudioTracksUseCase(AudioRepositoryImpl(context.applicationContext)),
                context.applicationContext
            ) as T
        }
    }

    return ViewModelProvider(
        store,
        factory
    )[AudioViewModel::class.java]
}