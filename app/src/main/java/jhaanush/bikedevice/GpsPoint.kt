package jhaanush.bikedevice

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gps_points",
    foreignKeys = [ForeignKey(entity = Ride::class, parentColumns = ["id"], childColumns = ["rideId"])],
    indices = [Index("rideId")]
)
data class GpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rideId: Long,
    val timestamp: Long,
    val lat: Double,
    val lon: Double
)
