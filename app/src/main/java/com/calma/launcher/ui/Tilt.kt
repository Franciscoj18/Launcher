package com.calma.launcher.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.mutableFloatStateOf

/**
 * Lee la inclinación del teléfono para mover el fondo un poquito (efecto de profundidad).
 * Se recentra solo: responde al movimiento, no a cómo sostienes el teléfono.
 */
class Tilt(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** De -1 a 1. */
    val x = mutableFloatStateOf(0f)
    val y = mutableFloatStateOf(0f)

    private var fx = 0f
    private var fy = 0f
    private var baseX = 0f
    private var baseY = Float.NaN
    private var running = false

    val available: Boolean get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = true
        baseY = Float.NaN
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(this)
        x.floatValue = 0f
        y.floatValue = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ax = e.values[0]
        val ay = e.values[1]
        // Suavizado
        fx += (ax - fx) * 0.12f
        fy += (ay - fy) * 0.12f
        if (baseY.isNaN()) { baseX = fx; baseY = fy }
        // La base sigue lentamente la postura, así el fondo vuelve al centro
        baseX += (fx - baseX) * 0.015f
        baseY += (fy - baseY) * 0.015f
        x.floatValue = ((fx - baseX) / 2.5f).coerceIn(-1f, 1f)
        y.floatValue = ((fy - baseY) / 2.5f).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
