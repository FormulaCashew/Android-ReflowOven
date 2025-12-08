# Reflow Oven Controller

An Android application built with Jetpack Compose and Kotlin to monitor and control a DIY Reflow Oven. The application is designed to communicate via TCP sockets with an ESP32 or any WiFi-enabled microcontroller.

The system uses a microcontroller to facilitate Closed-Loop PID control. It utilizes a controlled rectifier PCB to regulate power delivered to the oven and measures temperature using a thermocouple.

## Project Structure

*   **Application/**: Contains the Android application source code.
*   **Hardware/**: Contains schematics and PCB designs for the controlled rectifier and measurement circuits.
*   **Firmware/**: Contains the code for the microcontroller.
*   **test/**: Contains a script to simulate the behavior of the microcontroller for testing the app without hardware.

## Features

*   **Real-Time Monitoring**: Visualizes temperature curves.
*   **Profile Management**: Supports custom and pre-defined soldering profiles.
*   **Device Control**: Start, Stop, and status monitoring commands.

## Architecture

The Android app follows Clean Architecture and MVVM principles using:
*   **UI**: Jetpack Compose (Material 3)
*   **Async**: Kotlin Coroutines & Flows
*   **Charting**: MPAndroidChart

## Communication Protocol

The App acts as a TCP Client connecting to the microcontroller (Server).

**Commands (App -> Microcontroller)**
*   `STATUS?`: Request current status.
*   `START;{soak_temp};{soak_time};{reflow_temp};{reflow_time}`: Begin feedback loop with parameters.
*   `STOP`: Stop the process.

**Response**
*   `STATUS;{current_temp};{target_temp};{state};{timer}`

