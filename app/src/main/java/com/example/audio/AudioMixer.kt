package com.example.audio

object AudioMixer {
    /**
     * Splits and mixes the PCM channel based on requested stereo mode.
     * Applies scaling for decimal volume and guards against Short overflow.
     */
    fun mixAndScale(left: Short, right: Short, mode: StereoMode, volume: Float): Short {
        val mixed: Float = when (mode) {
            StereoMode.LEFT -> left.toFloat()
            StereoMode.RIGHT -> right.toFloat()
            StereoMode.MONO -> (left + right) / 2f
        }
        val scaled = mixed * volume
        return Math.max(Short.MIN_VALUE.toFloat(), Math.min(Short.MAX_VALUE.toFloat(), scaled)).toInt().toShort()
    }
}
