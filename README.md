# Dual Audio Streamer for Android

This project implements dual simultaneous Bluetooth audio streaming using custom PCM splitting.

## Architecture

* **UI Framework**: Jetpack Compose and Material 3
* **Audio Routing**: Custom `AudioProcessor` leveraging `AudioTrack` and manual PCM processing.
* **Bluetooth**: Utilizes A2DP reflection proxies to manually force `connect()` on multiple audio devices, bypassing generic system routing restrictions.

## How A2DP Dual Routing works

Standard Android audio system limits Output Streams to a single primary media device at a time, governed by the `AudioPolicyManager`. Hardware vendors (e.g. Samsung Dual Audio) modify the base system ROM to allow dual output streams directly in the HAL (Hardware Abstraction Layer).
To mimic this programmatically in software:
* We discover paired Bluetooth Audio devices.
* We try to manually invoke `connect()` on the `BluetoothA2dp` profile to keep both active in the stack.
* We create two decoupled `AudioTrack` instances using standard `MODE_STREAM`.
* Upon API >= 28, we use `setPreferredDevice()` and target each track's `AudioDeviceInfo` specific to each speaker.
* We synthesize (or capture via `MediaProjection`) a single stereo PCM buffer, manually split the Left and Right channels inside our `CoroutineScope(Dispatchers.IO)`, apply volume scale multiplication, and interleave it back.
* We then push it tightly into the two parallel tracks. 
* Note: Depending on the specific OEM ROM and `bt_stack` limits, Android may forcefully disconnect the secondary A2DP device. In those cases, a dedicated L2CAP socket connection would be needed, which is rarely allowed.

## Tested Devices 
* Pixel Series: `setPreferredDevice` generally functions over independent topologies if hardware allows Dual streams.
* Samsung devices natively combine routes making manual splits sometimes conflict with system behavior.

## Permissions Required
Because we control underlying connectivity:
- `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- `MODIFY_AUDIO_SETTINGS`
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`
