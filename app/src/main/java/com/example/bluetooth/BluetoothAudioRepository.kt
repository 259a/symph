package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Method

class BluetoothAudioRepository(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private var a2dpProfile: BluetoothProfile? = null

    init {
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProfile = proxy
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProfile = null
                }
            }
        }, BluetoothProfile.A2DP)
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        val paired = bluetoothAdapter?.bondedDevices?.filter {
            // A2DP sink profile filter (Major class Audio/Video)
            it.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
        } ?: emptyList()
        _pairedDevices.value = paired.toList()
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice): Boolean {
        return try {
            val a2dp = a2dpProfile ?: return false
            // Find hidden 'connect' method in BluetoothA2dp
            val method: Method = a2dp.javaClass.getDeclaredMethod("connect", BluetoothDevice::class.java)
            method.invoke(a2dp, device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e("BluetoothAudioRepo", "A2DP connect proxy failed fallback to Socket", e)
            false
        }
    }
}
