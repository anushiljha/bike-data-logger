package jhaanush.bikedevice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RideDao {
    @Insert
    suspend fun insert(ride: Ride): Long

    @Query("UPDATE rides SET endTime = :endTime WHERE id = :rideId")
    suspend fun setEndTime(rideId: Long, endTime: Long)

    @Query("SELECT * FROM rides")
    suspend fun getAll(): List<Ride>

    @Query("SELECT * FROM rides ORDER BY id DESC LIMIT 1")
    suspend fun getMostRecent(): Ride?
}
