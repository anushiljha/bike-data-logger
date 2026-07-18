package jhaanush.bikedevice

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GpsPoint::class, SensorReading::class, Ride::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite can't relax x/y/z's NOT NULL via ALTER TABLE, so rebuild the table:
                // new schema -> copy rows (tagged as the only sensor ever logged so far) -> swap in.
                db.execSQL(
                    """
                    CREATE TABLE sensor_readings_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        rideId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sensor_type TEXT NOT NULL,
                        x REAL,
                        y REAL,
                        z REAL,
                        scalar_value REAL,
                        FOREIGN KEY(rideId) REFERENCES rides(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO sensor_readings_new (id, rideId, timestamp, sensor_type, x, y, z, scalar_value)
                    SELECT id, rideId, timestamp, 'accelerometer', x, y, z, NULL FROM sensor_readings
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE sensor_readings")
                db.execSQL("ALTER TABLE sensor_readings_new RENAME TO sensor_readings")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_rideId ON sensor_readings(rideId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bike_data.db"
                ).addMigrations(MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
