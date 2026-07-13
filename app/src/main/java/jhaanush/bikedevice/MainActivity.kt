package jhaanush.bikedevice

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    private var isLoggingSensors = false
    private val sensorBuffer = mutableListOf<SensorReading>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        db = AppDatabase.getInstance(this)

        findViewById<Button>(R.id.btnInsert).setOnClickListener {
            lifecycleScope.launch {
                insertOneRealLocation()
            }
        }

        findViewById<Button>(R.id.btnStartLogging).setOnClickListener {
            lifecycleScope.launch {
                repeat(6)
                {
                    insertOneRealLocation()
                    delay(5000)
                }
            }
        }

        findViewById<Button>(R.id.btnQuery).setOnClickListener {
            lifecycleScope.launch {
                val points = db.gpsPointDao().getAll()
                points.forEach { Log.d("DB", it.toString()) }
            }
        }

        findViewById<Button>(R.id.btnStartSensorLogging).setOnClickListener {
            lifecycleScope.launch {
                sensorBuffer.clear()
                isLoggingSensors = true
                delay(5000)
                isLoggingSensors = false
                db.sensorReadingDao().insertAll(sensorBuffer)
                Log.d("SENSOR", "logged ${sensorBuffer.size} rows")
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun insertOneRealLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPS", "permission not granted yet, ignoring button press")
            return
        }
        val location = try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("GPS", "location request failed: ${e.message}")
            return
        }
        if (location != null) {
            db.gpsPointDao().insert(
                GpsPoint(timestamp = System.currentTimeMillis(), lat = location.latitude, lon = location.longitude)
            )
        } else {
            Log.d("GPS", "location was null — set a fake GPS location in emulator extended controls")
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        Log.d("SENSOR", "x=$x y=$y z=$z")

        if (isLoggingSensors) {
            sensorBuffer.add(SensorReading(timestamp = System.currentTimeMillis(), x = x, y = y, z = z))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}