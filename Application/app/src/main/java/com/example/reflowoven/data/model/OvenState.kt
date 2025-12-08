package com.example.reflowoven.data.model

data class OvenState(
    val currentTemperature: Float = 0f,
    val targetTemperature: Float = 0f,
    val stage: String = "Idle",
    val timeElapsed: Long = 0L,
    val status: String = "Idle"
)
