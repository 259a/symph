package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.audio.AudioProcessingService
import com.example.audio.StereoMode
import com.example.ui.DualAudioViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlin.math.log10

class MainActivity : ComponentActivity() {

    private val viewModel: DualAudioViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val permissions = mutableListOf(
                    Manifest.permission.RECORD_AUDIO
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                val permissionsState = rememberMultiplePermissionsState(permissions)
                
                LaunchedEffect(Unit) {
                    permissionsState.launchMultiplePermissionRequest()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsState.allPermissionsGranted) {
                        DualAudioScreen(viewModel)
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Please grant permissions to use Dual Audio")
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                                    Text("Grant Permissions")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualAudioScreen(viewModel: DualAudioViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Audio Streamer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.scanDevices() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan")
                Spacer(Modifier.width(8.dp))
                Text("Scan Paired Devices")
            }

            // Top section: paired devices list to select
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(pairedDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            if (uiState.speakerA == null) viewModel.selectSpeakerA(device)
                            else if (uiState.speakerB == null) viewModel.selectSpeakerB(device)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = "BT")
                            Spacer(Modifier.width(16.dp))
                            Text(device.name ?: device.address)
                        }
                    }
                }
            }

            // Middle section: speakers cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeakerCard(
                    title = "Speaker A",
                    device = uiState.speakerA?.name ?: "Tap to select from list above",
                    status = uiState.statusA,
                    volume = uiState.volumeA,
                    mode = uiState.modeA,
                    onVolumeChange = { viewModel.setVolumeA(it) },
                    onModeChange = { viewModel.setModeA(it) },
                    modifier = Modifier.weight(1f)
                )

                SpeakerCard(
                    title = "Speaker B",
                    device = uiState.speakerB?.name ?: "Tap to select from list above",
                    status = uiState.statusB,
                    volume = uiState.volumeB,
                    mode = uiState.modeB,
                    onVolumeChange = { viewModel.setVolumeB(it) },
                    onModeChange = { viewModel.setModeB(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Transport controls
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Link Volumes")
                        Switch(checked = uiState.linkedVolumes, onCheckedChange = { viewModel.toggleLinkVolumes(it) })
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Smart Reconnect")
                        Switch(checked = uiState.smartReconnectEnabled, onCheckedChange = { viewModel.setSmartReconnect(it) })
                    }
                    
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledIconButton(onClick = { 
                            val i = Intent(context, AudioProcessingService::class.java).apply { action = "START" }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(i)
                            } else {
                                context.startService(i)
                            }
                         }) {
                            Icon(Icons.Default.PlayArrow, "Play")
                        }
                        FilledIconButton(onClick = { 
                            val i = Intent(context, AudioProcessingService::class.java).apply { action = "STOP" }
                            context.startService(i)
                        }) {
                            Icon(Icons.Default.Stop, "Stop")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerCard(
    title: String,
    device: String,
    status: String,
    volume: Float,
    mode: StereoMode,
    onVolumeChange: (Float) -> Unit,
    onModeChange: (StereoMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(device, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            
            AssistChip(
                onClick = {}, 
                label = { Text(status) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (status == "Connected") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            )

            Spacer(Modifier.height(16.dp))

            // Mode Selector
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = mode == StereoMode.LEFT,
                    onClick = { onModeChange(StereoMode.LEFT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("L") }
                SegmentedButton(
                    selected = mode == StereoMode.MONO,
                    onClick = { onModeChange(StereoMode.MONO) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("M") }
                SegmentedButton(
                    selected = mode == StereoMode.RIGHT,
                    onClick = { onModeChange(StereoMode.RIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("R") }
            }

            Spacer(Modifier.height(16.dp))

            // Volume Slider + dB
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, "Volume")
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f)
                )
            }
            val db = if (volume > 0.01f) 20 * log10(volume) else -80f
            Text(String.format("%.1f dB", db), style = MaterialTheme.typography.bodySmall)
        }
    }
}
