package jhaanush.bikedevice

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gps_points")
data class GpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val lat: Double,
    val lon: Double
)
