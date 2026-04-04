package au.roman.bloodpressuretracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureDao {
    @Insert
    suspend fun insert(record: BloodPressureRecord)

    @Insert
    suspend fun insertAll(records: List<BloodPressureRecord>)

    @Delete
    suspend fun delete(record: BloodPressureRecord)

    @Query("DELETE FROM blood_pressure_records")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(records: List<BloodPressureRecord>) {
        deleteAll()
        insertAll(records)
    }

    @Query("SELECT * FROM blood_pressure_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BloodPressureRecord>>
}
