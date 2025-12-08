package com.example.reflowoven.data.model

data class ReflowProfile(
    val name: String,
    val stages: List<ProfileStage>
)

data class ProfileStage(
    val name: String,
    val targetTemperature: Float,
    val duration: Long
)
