package jhaanush.bikedevice

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "street_segments")
data class StreetSegment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double
)
