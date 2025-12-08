package com.example.reflowoven.data.repository

import com.example.reflowoven.data.communication.CommunicationService
import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import kotlinx.coroutines.flow.Flow

class ReflowOvenRepository(private val communicationService: CommunicationService) {

    fun connect(ipAddress: String, port: Int): Flow<Boolean> {
        return communicationService.connect(ipAddress, port)
    }

    suspend fun disconnect() {
        communicationService.disconnect()
    }

    suspend fun sendProfile(profile: ReflowProfile) {
        communicationService.sendProfile(profile)
    }

    suspend fun startOven(profile: ReflowProfile) {
        communicationService.startOven(profile)
    }

    suspend fun stopOven() {
        communicationService.stopOven()
    }

    fun getOvenState(): Flow<OvenState> {
        return communicationService.getOvenState()
    }
}
