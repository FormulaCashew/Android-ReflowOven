package com.example.reflowoven.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reflowoven.data.model.ProfileStage
import com.example.reflowoven.data.model.ReflowProfile
import com.example.reflowoven.ui.viewmodel.MainViewModel
import com.example.reflowoven.ui.viewmodel.MainViewModelFactory

@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory())) {
    val ovenState by viewModel.ovenState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val leadFreeProfile = ReflowProfile(
        name = "Lead-Free",
        stages = listOf(
            ProfileStage(name = "Soak", targetTemperature = 150f, duration = 90L),
            ProfileStage(name = "Reflow", targetTemperature = 245f, duration = 30L)
        )
    )

    val leadedProfile = ReflowProfile(
        name = "Leaded",
        stages = listOf(
            ProfileStage(name = "Soak", targetTemperature = 140f, duration = 60L),
            ProfileStage(name = "Reflow", targetTemperature = 215f, duration = 45L)
        )
    )

    val predefinedProfiles = listOf(leadFreeProfile, leadedProfile)

    if (showConnectionDialog) {
        ConnectionDialog(
            onConnect = {
                ip, port -> viewModel.connect(ip, port)
                showConnectionDialog = false
            },
            onDismiss = { showConnectionDialog = false }
        )
    }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            profiles = predefinedProfiles,
            onProfileSelected = {
                viewModel.startOven(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Current Temperature: ${ovenState.currentTemperature}°C")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Status: ${ovenState.status}")
        Spacer(modifier = Modifier.height(16.dp))

        if (isConnected) {
            Row {
                Button(onClick = { showProfileDialog = true }) {
                    Text("START")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.stopOven() }) {
                    Text("STOP")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect")
                }
            }
        } else {
            Button(onClick = { showConnectionDialog = true }) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ConnectionDialog(
    onConnect: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8080") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Connection Parameters") },
        text = {
            Column {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(ipAddress, port.toIntOrNull() ?: 8080) }
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileSelectionDialog(
    profiles: List<ReflowProfile>,
    onProfileSelected: (ReflowProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomProfileDialog by remember { mutableStateOf(false) }

    if (showCustomProfileDialog) {
        CustomProfileDialog(
            onProfileCreated = {
                onProfileSelected(it)
                showCustomProfileDialog = false
             },
            onDismiss = { showCustomProfileDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select a Profile") },
        text = {
            LazyColumn {
                items(profiles) { profile ->
                    Text(
                        text = profile.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProfileSelected(profile) }
                            .padding(vertical = 12.dp)
                    )
                }
                item {
                    Text(
                        text = "Custom...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomProfileDialog = true }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomProfileDialog(
    onProfileCreated: (ReflowProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Custom") }
    var soakTemp by remember { mutableStateOf("150") }
    var soakTime by remember { mutableStateOf("90") }
    var reflowTemp by remember { mutableStateOf("250") }
    var reflowTime by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Profile") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Profile Name") })
                Spacer(Modifier.height(8.dp))
                TextField(value = soakTemp, onValueChange = { soakTemp = it }, label = { Text("Soak Temp (°C)") })
                Spacer(Modifier.height(8.dp))
                TextField(value = soakTime, onValueChange = { soakTime = it }, label = { Text("Soak Time (s)") })
                Spacer(Modifier.height(8.dp))
                TextField(value = reflowTemp, onValueChange = { reflowTemp = it }, label = { Text("Reflow Temp (°C)") })
                Spacer(Modifier.height(8.dp))
                TextField(value = reflowTime, onValueChange = { reflowTime = it }, label = { Text("Reflow Time (s)") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val profile = ReflowProfile(
                        name = name,
                        stages = listOf(
                            ProfileStage("Soak", soakTemp.toFloatOrNull() ?: 150f, soakTime.toLongOrNull() ?: 90L),
                            ProfileStage("Reflow", reflowTemp.toFloatOrNull() ?: 250f, reflowTime.toLongOrNull() ?: 30L)
                        )
                    )
                    onProfileCreated(profile)
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
