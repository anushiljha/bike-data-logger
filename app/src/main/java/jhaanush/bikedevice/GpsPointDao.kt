package jhaanush.bikedevice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsPointDao {
    @Insert
    suspend fun insert(point: GpsPoint)

    @Query("SELECT * FROM gps_points")
    suspend fun getAll(): List<GpsPoint>
}
