package jhaanush.bikedevice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorReadingDao {
    @Insert
    suspend fun insertAll(readings: List<SensorReading>)

    @Query("SELECT * FROM sensor_readings")
    suspend fun getAll(): List<SensorReading>

    @Query("SELECT * FROM sensor_readings WHERE rideId = :rideId ORDER BY timestamp")
    suspend fun getForRide(rideId: Long): List<SensorReading>
}
