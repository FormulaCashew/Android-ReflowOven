package com.example.reflowoven.data.communication

import android.util.Log
import com.example.reflowoven.data.model.OvenState
import com.example.reflowoven.data.model.ReflowProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.util.Scanner

class WiFiService : CommunicationService {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: Scanner? = null

    companion object {
        private const val TAG = "WiFiService"
    }

    override fun connect(ipAddress: String, port: Int): Flow<Boolean> = flow {
        try {
            socket = Socket(ipAddress, port)
            writer = PrintWriter(socket!!.getOutputStream(), true)
            reader = Scanner(socket!!.getInputStream())
            Log.d(TAG, "Connected to $ipAddress:$port")
            emit(true)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            emit(false)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
                writer?.close()
                reader?.close()
                Log.d(TAG, "Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error while disconnecting", e)
            }
        }
    }

    override suspend fun sendProfile(profile: ReflowProfile) {
        withContext(Dispatchers.IO) {
            val command = "PROFILE;${profile.stages.joinToString(";") { "${it.targetTemperature};${it.duration}" }}"
            sendCommand("$command\n")
        }
    }

    override suspend fun startOven(profile: ReflowProfile) {
        withContext(Dispatchers.IO) {
            val command = "START;${profile.stages.joinToString(";") { "${it.targetTemperature};${it.duration}" }}"
            sendCommand("$command\n")
        }
    }

    override suspend fun stopOven() {
        withContext(Dispatchers.IO) {
            sendCommand("STOP\n")
        }
    }

    override fun getOvenState(): Flow<OvenState> = flow {
        while (socket?.isConnected == true) {
            sendCommand("STATUS?\n")
            if (reader?.hasNextLine() == true) {
                val line = reader!!.nextLine()
                Log.d(TAG, "Received: $line")
                val parts = line.split(';')
                if (parts.isNotEmpty() && parts[0] == "STATUS") {
                    try {
                        val ovenState = OvenState(
                            currentTemperature = parts[1].toFloat(),
                            targetTemperature = parts[2].toFloat(),
                            stage = parts[3],
                            timeElapsed = parts[4].toLong(),
                            status = parts[3]
                        )
                        emit(ovenState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing status: $line", e)
                    }
                }
            }
            delay(1000) // Poll every second
        }
    }.flowOn(Dispatchers.IO)

    private fun sendCommand(command: String) {
        writer?.let {
            try {
                it.print(command)
                it.flush()
                if (it.checkError()) {
                    throw IOException("PrintWriter error")
                }
                Log.d(TAG, "Sent command: ${command.trim()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command: ${command.trim()}", e)
            }
        }
    }
}
