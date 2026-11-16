package com.aniket.fitnesslibrary

import android.app.Application
import androidx.core.text.HtmlCompat
import androidx.room.Room
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class App : Application() {
    lateinit var repo: Repository
    override fun onCreate() {
        super.onCreate()
        val api = Retrofit.Builder().baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()).build().create(WgerApi::class.java)
        val db = Room.databaseBuilder(this, AppDb::class.java, "fitness.db").build()
        repo = Repository(api, db.dao())
    }
}

data class Exercise(
    val id: Int, val name: String, val category: String, val imageUrl: String?,
    val description: String = "", val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(), val images: List<String> = emptyList(),
)

private fun fullUrl(p: String?) = p?.let { if (it.startsWith("http")) it else "https://wger.de$it" }

private fun ExerciseInfoDto.toExercise(): Exercise? {
    val t = translations?.firstOrNull { it.language == ENGLISH && !it.name.isNullOrBlank() } ?: return null
    val imgs = images.orEmpty().sortedByDescending { it.is_main }.mapNotNull { fullUrl(it.image) }
    return Exercise(
        id = id, name = t.name!!, category = category?.name ?: "Other", imageUrl = imgs.firstOrNull(),
        description = HtmlCompat.fromHtml(t.description.orEmpty(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim(),
        muscles = muscles.orEmpty().mapNotNull { it.name_en?.takeIf { s -> s.isNotBlank() } ?: it.name },
        equipment = equipment.orEmpty().mapNotNull { it.name },
        images = imgs,
    )
}

class Repository(private val api: WgerApi, private val dao: FitDao) {
    suspend fun categories() = api.categories().results

    suspend fun page(offset: Int, category: Int?): Pair<List<Exercise>, Boolean> {
        val p = api.exercises(offset = offset, category = category)
        return p.results.mapNotNull { it.toExercise() } to (p.next != null)
    }

    /** Only [PER_CATEGORY] exercises for each body part (or for the one selected category). */
    suspend fun featured(category: Int?): List<Exercise> = coroutineScope {
        val ids = category?.let { listOf(it) } ?: api.categories().results.map { it.id }
        ids.map { id ->
            async { api.exercises(limit = 10, category = id).results.mapNotNull { it.toExercise() }.take(PER_CATEGORY) }
        }.awaitAll().flatten()
    }

    suspend fun search(term: String): List<Exercise> = api.search(term).suggestions.mapNotNull { s ->
        val d = s.data
        val name = d.name ?: s.value ?: return@mapNotNull null
        Exercise(d.base_id ?: d.id, name, d.category ?: "Other", fullUrl(d.image))
    }.distinctBy { it.id }

    suspend fun exercise(id: Int): Exercise =
        api.exercise(id).toExercise() ?: error("This exercise has no English description")

    // Workout plans (CRUD)
    fun plans() = dao.plans()
    fun plan(id: Long) = dao.plan(id)
    fun planExercises(id: Long) = dao.planExercises(id)
    suspend fun createPlan(name: String) = dao.insertPlan(PlanEntity(name = name.trim()))
    suspend fun deletePlan(id: Long) = dao.deletePlan(id)
    suspend fun addToPlan(planId: Long, e: Exercise) =
        dao.insertPlanExercise(PlanExerciseEntity(planId, e.id, e.name, e.category, e.imageUrl))
    suspend fun removeFromPlan(planId: Long, exerciseId: Int) = dao.removeExercise(planId, exerciseId)

    // Completed workouts
    suspend fun logWorkout(planName: String, count: Int) =
        dao.insertCompleted(CompletedEntity(planName = planName, exerciseCount = count, completedAt = System.currentTimeMillis()))
    fun history() = dao.history()
    suspend fun deleteHistory(id: Long) = dao.deleteCompleted(id)
}
