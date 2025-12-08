package com.example.reflowoven.data.communication

import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import kotlinx.coroutines.flow.Flow

interface CommunicationService {
    fun connect(ipAddress: String, port: Int): Flow<Boolean>
    suspend fun disconnect()
    suspend fun sendProfile(profile: ReflowProfile)
    suspend fun startOven(profile: ReflowProfile)
    suspend fun stopOven()
    fun getOvenState(): Flow<OvenState>
}
