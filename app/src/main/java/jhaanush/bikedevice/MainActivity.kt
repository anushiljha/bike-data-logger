package jhaanush.bikedevice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rideTimerText: TextView
    private lateinit var speedText: TextView
    private var isRideActive = false
    private var timerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)
        rideTimerText = findViewById(R.id.tvRideTimer)
        speedText = findViewById(R.id.tvSpeed)

        findViewById<Button>(R.id.btnStartRide).setOnClickListener {
            if (!isRideActive) startRide()
        }

        findViewById<Button>(R.id.btnStopRide).setOnClickListener {
            if (isRideActive) stopRide()
        }

        findViewById<Button>(R.id.btnQuery).setOnClickListener {
            lifecycleScope.launch {
                val points = db.gpsPointDao().getAll()
                points.forEach { Log.d("DB", it.toString()) }
            }
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            lifecycleScope.launch {
                try {
                    exportMostRecentRide()
                } catch (e: Exception) {
                    Log.e("EXPORT", "export failed", e)
                    Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.btnImportStreets).setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(this@MainActivity, "Importing street data…", Toast.LENGTH_SHORT).show()
                try {
                    val count = importStreetSegments()
                    if (count < 0) {
                        Toast.makeText(this@MainActivity, "No street data file found", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Imported $count street segments", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("IMPORT", "street import failed", e)
                    Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val permissionsNeeded = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }

        lifecycleScope.launch {
            val activeRide = db.rideDao().getActiveRide()
            if (activeRide != null) {
                isRideActive = true
                startTimerTicking(activeRide.startTime)
            }
        }
    }

    private fun startRide() {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val rideId = db.rideDao().insert(Ride(startTime = startTime))
            val intent = Intent(this@MainActivity, LoggingService::class.java)
                .putExtra(LoggingService.EXTRA_RIDE_ID, rideId)
            ContextCompat.startForegroundService(this@MainActivity, intent)
            isRideActive = true
            startTimerTicking(startTime)
        }
    }

    private fun startTimerTicking(startTime: Long) {
        timerJob = lifecycleScope.launch {
            while (true) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                rideTimerText.text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                updateSpeedDisplay()
                delay(1000)
            }
        }
    }

    private fun updateSpeedDisplay() {
        val update = LoggingService.currentUpdate.value
        val staleAfterMs = 2000L
        if (update == null || System.currentTimeMillis() - update.timestampMs > staleAfterMs) {
            speedText.text = "-- mph"
        } else {
            val mph = update.speedMps * 2.23694f
            speedText.text = String.format("%.1f mph", mph)
        }
    }

    private fun stopRide() {
        val intent = Intent(this, LoggingService::class.java).setAction(LoggingService.ACTION_STOP)
        startService(intent)
        isRideActive = false
        timerJob?.cancel()
        speedText.text = "-- mph"
    }

    private suspend fun exportMostRecentRide() {
        val ride = db.rideDao().getMostRecent()
        if (ride == null) {
            Toast.makeText(this, "No rides to export", Toast.LENGTH_SHORT).show()
            return
        }
        val gpsPoints = db.gpsPointDao().getForRide(ride.id)
        val sensorReadings = db.sensorReadingDao().getForRide(ride.id)

        val exportDir = withContext(Dispatchers.IO) {
            val base = getExternalFilesDir(null)
            Log.d("EXPORT", "getExternalFilesDir(null) = $base")
            val dir = File(base, "exports")
            val created = dir.mkdirs()
            Log.d("EXPORT", "dir=${dir.absolutePath} mkdirs()=$created exists=${dir.exists()}")

            File(dir, "ride_${ride.id}.csv").printWriter().use { out ->
                out.println("id,startTime,endTime")
                out.println("${ride.id},${ride.startTime},${ride.endTime}")
            }
            File(dir, "ride_${ride.id}_gps.csv").printWriter().use { out ->
                out.println("id,rideId,timestamp,lat,lon")
                gpsPoints.forEach { out.println("${it.id},${it.rideId},${it.timestamp},${it.lat},${it.lon}") }
            }
            File(dir, "ride_${ride.id}_sensors.csv").printWriter().use { out ->
                out.println("id,rideId,timestamp,sensorType,x,y,z,scalarValue")
                sensorReadings.forEach { out.println("${it.id},${it.rideId},${it.timestamp},${it.sensorType},${it.x},${it.y},${it.z},${it.scalarValue}") }
            }
            dir
        }
        Log.d("EXPORT", "Exported ride ${ride.id} to ${exportDir.path}")
        Toast.makeText(this, "Ride exported", Toast.LENGTH_SHORT).show()
        rideTimerText.text = "00:00"
    }

    private suspend fun importStreetSegments(): Int = withContext(Dispatchers.IO) {
        val file = File(getExternalFilesDir(null), "map_data/street_segments.csv")
        Log.d("IMPORT", "importing from ${file.absolutePath}, exists=${file.exists()}")
        if (!file.exists()) {
            return@withContext -1
        }

        var count = 0
        val batch = mutableListOf<StreetSegment>()
        file.bufferedReader().use { reader ->
            reader.readLine() // header
            var line = reader.readLine()
            while (line != null) {
                val parts = line.split(",")
                batch.add(
                    StreetSegment(
                        lat1 = parts[0].toDouble(),
                        lon1 = parts[1].toDouble(),
                        lat2 = parts[2].toDouble(),
                        lon2 = parts[3].toDouble()
                    )
                )
                count++
                if (batch.size >= 2000) {
                    db.streetSegmentDao().insertAll(batch)
                    batch.clear()
                }
                line = reader.readLine()
            }
        }
        if (batch.isNotEmpty()) {
            db.streetSegmentDao().insertAll(batch)
        }
        count
    }
}
