package jhaanush.bikedevice

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_readings",
    foreignKeys = [ForeignKey(entity = Ride::class, parentColumns = ["id"], childColumns = ["rideId"])],
    indices = [Index("rideId")]
)
data class SensorReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rideId: Long,
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
