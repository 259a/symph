package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.StereoMode
import com.example.bluetooth.BluetoothAudioRepository
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DualAudioViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothRepo = BluetoothAudioRepository(application)
    private val userPreferencesRepo = UserPreferencesRepository(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val pairedDevices = bluetoothRepo.pairedDevices

    private val _uiState = MutableStateFlow(DualAudioUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepo.smartReconnectFlow.collect { enabled ->
                _uiState.update { it.copy(smartReconnectEnabled = enabled) }
            }
        }
    }

    fun setSmartReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateSmartReconnect(enabled)
            // Note: DataStore will emit the new value and update uiState.smartReconnectEnabled via the init block collect
        }
    }

    fun scanDevices() {
        bluetoothRepo.loadPairedDevices()
    }

    fun selectSpeakerA(device: BluetoothDevice) {
        _uiState.update { it.copy(speakerA = device) }
        connectDeviceA(device)
    }

    fun selectSpeakerB(device: BluetoothDevice) {
        _uiState.update { it.copy(speakerB = device) }
        connectDeviceB(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectDeviceA(device: BluetoothDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusA = "Connecting...") }
            val success = bluetoothRepo.connectDevice(device)
            if (success) {
                _uiState.update { it.copy(statusA = "Connected") }
            } else {
                _uiState.update { it.copy(statusA = "Error") }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDeviceB(device: BluetoothDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusB = "Connecting...") }
            val success = bluetoothRepo.connectDevice(device)
            if (success) {
                _uiState.update { it.copy(statusB = "Connected") }
            } else {
                _uiState.update { it.copy(statusB = "Error") }
            }
        }
    }

    fun setVolumeA(volume: Float) {
        _uiState.update { it.copy(volumeA = volume) }
        if (uiState.value.linkedVolumes) {
            _uiState.update { it.copy(volumeB = volume) }
        }
    }

    fun setVolumeB(volume: Float) {
        _uiState.update { it.copy(volumeB = volume) }
        if (uiState.value.linkedVolumes) {
            _uiState.update { it.copy(volumeA = volume) }
        }
    }

    fun toggleLinkVolumes(linked: Boolean) {
        _uiState.update { it.copy(linkedVolumes = linked) }
        if (linked) {
            _uiState.update { it.copy(volumeB = it.volumeA) }
        }
    }

    fun setModeA(mode: StereoMode) {
        _uiState.update { it.copy(modeA = mode) }
    }

    fun setModeB(mode: StereoMode) {
        _uiState.update { it.copy(modeB = mode) }
    }
}

data class DualAudioUiState(
    val speakerA: BluetoothDevice? = null,
    val speakerB: BluetoothDevice? = null,
    val statusA: String = "Disconnected",
    val statusB: String = "Disconnected",
    val volumeA: Float = 0.8f,
    val volumeB: Float = 0.8f,
    val modeA: StereoMode = StereoMode.LEFT,
    val modeB: StereoMode = StereoMode.RIGHT,
    val linkedVolumes: Boolean = false,
    val isPlaying: Boolean = false,
    val smartReconnectEnabled: Boolean = true
)
