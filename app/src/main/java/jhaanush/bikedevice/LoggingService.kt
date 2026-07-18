package jhaanush.bikedevice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoggingService : LifecycleService(), SensorEventListener {

    companion object {
        const val EXTRA_RIDE_ID = "rideId"
        const val ACTION_STOP = "jhaanush.bikedevice.STOP_RIDE"
        private const val CHANNEL_ID = "ride_logging"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    private var rideId: Long = -1
    private val sensorBuffer = mutableListOf<SensorReading>()
    private var loggingJob: Job? = null
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopRide()
            return START_NOT_STICKY
        }

        rideId = intent?.getLongExtra(EXTRA_RIDE_ID, -1) ?: -1
        startForeground(NOTIFICATION_ID, buildNotification())
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        startLoggingLoops()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Ride logging", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bike Data Logger")
            .setContentText("Recording ride")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun startLoggingLoops() {
        startLocationUpdates()
        loggingJob = lifecycleScope.launch {
            while (true) {
                delay(5000)
                flushSensorBuffer()
            }
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lifecycleScope.launch {
                    db.gpsPointDao().insert(
                        GpsPoint(
                            rideId = rideId,
                            timestamp = System.currentTimeMillis(),
                            lat = location.latitude,
                            lon = location.longitude
                        )
                    )
                }
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private suspend fun flushSensorBuffer() {
        if (sensorBuffer.isEmpty()) return
        val toInsert = sensorBuffer.toList()
        sensorBuffer.clear()
        db.sensorReadingDao().insertAll(toInsert)
    }

    private fun stopRide() {
        loggingJob?.cancel()
        sensorManager.unregisterListener(this)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        val endedRideId = rideId
        lifecycleScope.launch {
            flushSensorBuffer()
            val idToClose = if (endedRideId != -1L) endedRideId else db.rideDao().getActiveRide()?.id ?: -1L
            if (idToClose != -1L) {
                db.rideDao().setEndTime(idToClose, System.currentTimeMillis())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        sensorBuffer.add(
            SensorReading(
                rideId = rideId,
                timestamp = System.currentTimeMillis(),
                sensorType = "accelerometer",
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                scalarValue = null
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
