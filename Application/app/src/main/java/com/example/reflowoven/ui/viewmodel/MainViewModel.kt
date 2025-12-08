package com.example.reflowoven.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import com.example.reflowoven.data.repository.ReflowOvenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ReflowOvenRepository) : ViewModel() {

    private val _ovenState = MutableStateFlow(OvenState())
    val ovenState: StateFlow<OvenState> = _ovenState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var ovenStateJob: Job? = null

    fun connect(ipAddress: String, port: Int) {
        viewModelScope.launch {
            repository.connect(ipAddress, port).collect { isConnected ->
                _isConnected.value = isConnected
                if (isConnected) {
                    ovenStateJob?.cancel()
                    ovenStateJob = viewModelScope.launch {
                        repository.getOvenState().collect { state ->
                            _ovenState.value = state
                        }
                    }
                } else {
                    ovenStateJob?.cancel()
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            ovenStateJob?.cancel()
            repository.disconnect()
            _isConnected.value = false
            _ovenState.value = OvenState() // Reset state
        }
    }

    fun sendProfile(profile: ReflowProfile) {
        viewModelScope.launch {
            repository.sendProfile(profile)
        }
    }

    fun startOven(profile: ReflowProfile) {
        viewModelScope.launch {
            repository.startOven(profile)
        }
    }

    fun stopOven() {
        viewModelScope.launch {
            repository.stopOven()
        }
    }
}
