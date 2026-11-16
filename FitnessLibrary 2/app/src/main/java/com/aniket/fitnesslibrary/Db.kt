package com.aniket.fitnesslibrary

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class PlanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(primaryKeys = ["planId", "exerciseId"])
data class PlanExerciseEntity(
    val planId: Long, val exerciseId: Int, val name: String,
    val category: String, val imageUrl: String?,
)

@Entity data class CompletedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planName: String, val exerciseCount: Int, val completedAt: Long,
)

data class PlanRow(val id: Long, val name: String, val exerciseCount: Int)

@Dao
abstract class FitDao {
    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM PlanExerciseEntity e WHERE e.planId = p.id) AS exerciseCount FROM PlanEntity p ORDER BY p.id DESC")
    abstract fun plans(): Flow<List<PlanRow>>

    @Query("SELECT * FROM PlanEntity WHERE id = :id") abstract fun plan(id: Long): Flow<PlanEntity?>
    @Query("SELECT * FROM PlanExerciseEntity WHERE planId = :id") abstract fun planExercises(id: Long): Flow<List<PlanExerciseEntity>>

    @Insert abstract suspend fun insertPlan(p: PlanEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPlanExercise(e: PlanExerciseEntity)
    @Query("DELETE FROM PlanExerciseEntity WHERE planId = :planId AND exerciseId = :exId")
    abstract suspend fun removeExercise(planId: Long, exId: Int)
    @Query("DELETE FROM PlanExerciseEntity WHERE planId = :id") abstract suspend fun clearPlan(id: Long)
    @Query("DELETE FROM PlanEntity WHERE id = :id") abstract suspend fun deletePlanRow(id: Long)

    @Transaction
    open suspend fun deletePlan(id: Long) { clearPlan(id); deletePlanRow(id) }

    @Insert abstract suspend fun insertCompleted(c: CompletedEntity)
    @Query("SELECT * FROM CompletedEntity ORDER BY completedAt DESC") abstract fun history(): Flow<List<CompletedEntity>>
    @Query("DELETE FROM CompletedEntity WHERE id = :id") abstract suspend fun deleteCompleted(id: Long)
}

@Database(
    entities = [PlanEntity::class, PlanExerciseEntity::class, CompletedEntity::class],
    version = 1, exportSchema = false,
)
abstract class AppDb : RoomDatabase() { abstract fun dao(): FitDao }
