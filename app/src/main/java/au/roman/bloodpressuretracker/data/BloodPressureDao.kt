package au.roman.bloodpressuretracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureDao {
    @Insert
    suspend fun insert(record: BloodPressureRecord)

    @Delete
    suspend fun delete(record: BloodPressureRecord)

    @Query("SELECT * FROM blood_pressure_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BloodPressureRecord>>
}
