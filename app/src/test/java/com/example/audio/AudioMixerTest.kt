package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMixerTest {

    @Test
    fun testLeftChannelRouting() {
        // Output should be Left * Volume
        val left: Short = 1000
        val right: Short = -1000
        val out = AudioMixer.mixAndScale(left, right, StereoMode.LEFT, 1.0f)
        assertEquals(1000.toShort(), out)
    }

    @Test
    fun testRightChannelRouting() {
        val left: Short = 1000
        val right: Short = -1000
        val out = AudioMixer.mixAndScale(left, right, StereoMode.RIGHT, 1.0f)
        assertEquals((-1000).toShort(), out)
    }

    @Test
    fun testMonoChannelMixing() {
        val left: Short = 1000
        val right: Short = -500
        // Mono averages (1000 - 500) / 2 = 250
        val out = AudioMixer.mixAndScale(left, right, StereoMode.MONO, 1.0f)
        assertEquals(250.toShort(), out)
    }

    @Test
    fun testVolumeScaling() {
        val left: Short = 2000
        val right: Short = 2000
        // Volume 50%
        val out = AudioMixer.mixAndScale(left, right, StereoMode.LEFT, 0.5f)
        assertEquals(1000.toShort(), out)
    }

    @Test
    fun testClippingPrevention() {
        val left: Short = 30000
        val right: Short = 30000
        // 2x Volume should safely clip at Short.MAX_VALUE
        val out = AudioMixer.mixAndScale(left, right, StereoMode.LEFT, 2.0f)
        assertEquals(Short.MAX_VALUE, out)
    }
}
