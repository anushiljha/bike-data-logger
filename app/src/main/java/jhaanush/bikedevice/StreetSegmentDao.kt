package jhaanush.bikedevice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StreetSegmentDao {
    @Insert
    suspend fun insertAll(segments: List<StreetSegment>)

    @Query("SELECT COUNT(*) FROM street_segments")
    suspend fun count(): Int
}
