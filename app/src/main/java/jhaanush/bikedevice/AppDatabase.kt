package jhaanush.bikedevice

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GpsPoint::class, SensorReading::class, Ride::class, StreetSegment::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun rideDao(): RideDao
    abstract fun streetSegmentDao(): StreetSegmentDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Brand-new table, no existing data to touch, so unlike MIGRATION_3_4
                // this is just a plain CREATE TABLE, no rebuild-and-copy needed.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS street_segments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        lat1 REAL NOT NULL,
                        lon1 REAL NOT NULL,
                        lat2 REAL NOT NULL,
                        lon2 REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bike_data.db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
