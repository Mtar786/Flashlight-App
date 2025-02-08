package com.Muhammad.flashlightapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Muhammad.flashlightapp.ui.theme.FlashlightAppTheme
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashOn = false
    private var isStrobeOn = false
    private lateinit var sensorManager: SensorManager
    private val handler = Handler(Looper.getMainLooper())

    private val strobeRunnable = object : Runnable {
        override fun run() {
            if (isStrobeOn) {
                toggleFlashlight(!isFlashOn) // Toggle flashlight state
                handler.postDelayed(this, 300) // Adjust strobe interval
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize CameraManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (hasFlash == true) {
                    cameraId = id
                    break
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        // Setup Shake Detector
        setupShakeDetector()

        setContent {
            FlashlightAppTheme {
                FlashlightScreen(
                    onToggleFlash = { toggleFlashlight(it) },
                    onToggleStrobe = { toggleStrobeMode(it) },
                    onSendSOS = { sendSOSSignal() },
                    onSetTimer = { duration -> turnOffFlashlightAfter(duration) }
                )
            }
        }
    }

    private fun toggleFlashlight(turnOn: Boolean) {
        isFlashOn = turnOn
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, turnOn)
            } ?: Toast.makeText(this, "No flash available", Toast.LENGTH_SHORT).show()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to access flashlight", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleStrobeMode(turnOn: Boolean) {
        isStrobeOn = turnOn
        if (isStrobeOn) {
            handler.post(strobeRunnable)
        } else {
            handler.removeCallbacks(strobeRunnable)
            toggleFlashlight(false) // Ensure flashlight is off
        }
    }

    private fun sendSOSSignal() {
        val sosPattern = listOf(200L, 200L, 200L, 600L, 200L, 600L, 200L, 200L, 200L)
        Thread {
            for (interval in sosPattern) {
                toggleFlashlight(true)
                Thread.sleep(interval)
                toggleFlashlight(false)
                Thread.sleep(200)
            }
        }.start()
    }

    private fun turnOffFlashlightAfter(duration: Long) {
        handler.postDelayed({
            toggleFlashlight(false)
        }, duration)
    }

    private fun setupShakeDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val x = event?.values?.get(0) ?: 0f
                val y = event?.values?.get(1) ?: 0f
                val z = event?.values?.get(2) ?: 0f
                val shakeMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                if (shakeMagnitude > 12) { // Adjust sensitivity
                    toggleFlashlight(!isFlashOn)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
}

@Composable
fun FlashlightScreen(
    onToggleFlash: (Boolean) -> Unit,
    onToggleStrobe: (Boolean) -> Unit,
    onSendSOS: () -> Unit,
    onSetTimer: (Long) -> Unit
) {
    var isFlashOn by remember { mutableStateOf(false) }
    var isStrobeOn by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isFlashOn || isStrobeOn) Color.Yellow else MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isFlashOn) "Flashlight ON" else "Flashlight OFF",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    isFlashOn = !isFlashOn
                    onToggleFlash(isFlashOn)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isFlashOn) Color.Green else Color.Red)
            ) {
                Text(text = if (isFlashOn) "Turn OFF" else "Turn ON")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { isStrobeOn = !isStrobeOn; onToggleStrobe(isStrobeOn) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isStrobeOn) Color.Magenta else Color.Gray)
            ) {
                Text(text = if (isStrobeOn) "Stop Strobe" else "Start Strobe")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onSendSOS() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text(text = "Send SOS")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { isDropdownExpanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
            ) {
                Text(text = "Set Timer")
            }
            DropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false }
            ) {
                listOf(5, 10, 30).forEach { seconds ->
                    CustomDropdownMenuItem(
                        onClick = {
                            onSetTimer(seconds * 1000L)
                            isDropdownExpanded = false
                        },
                        text = "$seconds seconds"
                    )
                }
            }
        }
    }
}

@Composable
fun CustomDropdownMenuItem(onClick: () -> Unit, text: String) {
    androidx.compose.material3.DropdownMenuItem(
        onClick = onClick,
        text = {
            Text(text = text)
        }
    )
}



