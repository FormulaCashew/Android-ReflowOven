package com.example.reflowoven.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import com.example.reflowoven.data.repository.ReflowOvenRepository
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ReflowOvenRepository) : ViewModel() {

    private val _ovenState = MutableStateFlow(OvenState())
    val ovenState: StateFlow<OvenState> = _ovenState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _tempHistory = MutableStateFlow<List<Entry>>(emptyList())
    val tempHistory: StateFlow<List<Entry>> = _tempHistory.asStateFlow()

    private var timeIndex = 0f

    private var ovenStateJob: Job? = null

    fun connect(ipAddress: String, port: Int) {
        viewModelScope.launch {
            repository.connect(ipAddress, port).collect { isConnected ->
                _isConnected.value = isConnected
                if (isConnected) {
                    ovenStateJob?.cancel()

                    // Reset graph data on new connection
                    _tempHistory.value = emptyList()
                    timeIndex = 0f

                    ovenStateJob = viewModelScope.launch {
                        repository.getOvenState().collect { state ->
                            _ovenState.value = state
                            // Update graph with new temp
                            addGraphPoint(state.currentTemperature)
                        }
                    }
                } else {
                    ovenStateJob?.cancel()
                }
            }
        }
    }

    private fun addGraphPoint(temp: Float) {
        val currentList = _tempHistory.value.toMutableList()
        currentList.add(Entry(timeIndex, temp))
        timeIndex += 1f

        if (currentList.size > 120) {
            currentList.removeAt(0)
        }
        _tempHistory.value = ArrayList(currentList)
    }

    fun disconnect() {
        viewModelScope.launch {
            ovenStateJob?.cancel()
            repository.disconnect()
            _isConnected.value = false
            _ovenState.value = OvenState()
            _tempHistory.value = emptyList()
        }
    }

    fun sendProfile(profile: ReflowProfile) {
        viewModelScope.launch { repository.sendProfile(profile) }
    }

    fun startOven(profile: ReflowProfile) {
        viewModelScope.launch {
            _tempHistory.value = emptyList()
            timeIndex = 0f
            repository.startOven(profile)
        }
    }

    fun stopOven() {
        viewModelScope.launch { repository.stopOven() }
    }
}