package com.example.reflowoven.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.reflowoven.data.communication.WiFiService
import com.example.reflowoven.data.repository.ReflowOvenRepository

class MainViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // In a real app, you'd use a dependency injection framework
            val communicationService = WiFiService()
            val repository = ReflowOvenRepository(communicationService)
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
