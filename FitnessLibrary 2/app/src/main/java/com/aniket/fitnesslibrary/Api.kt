package com.aniket.fitnesslibrary

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val BASE_URL = "https://wger.de/api/v2/"
const val ENGLISH = 2
const val PAGE = 20
const val PER_CATEGORY = 2

data class Page<T>(val count: Int = 0, val next: String? = null, val results: List<T> = emptyList())
data class CategoryDto(val id: Int, val name: String)
data class ImageDto(val image: String? = null, val is_main: Boolean = false)
data class TranslationDto(val name: String? = null, val description: String? = null, val language: Int = 0)
data class MuscleDto(val name: String? = null, val name_en: String? = null)
data class EquipmentDto(val name: String? = null)
data class ExerciseInfoDto(
    val id: Int,
    val category: CategoryDto? = null,
    val images: List<ImageDto>? = null,
    val translations: List<TranslationDto>? = null,
    val muscles: List<MuscleDto>? = null,
    val equipment: List<EquipmentDto>? = null,
)
data class SuggestionData(val id: Int, val base_id: Int? = null, val name: String? = null,
                          val category: String? = null, val image: String? = null)
data class Suggestion(val value: String? = null, val data: SuggestionData)
data class SearchResponse(val suggestions: List<Suggestion> = emptyList())

interface WgerApi {
    @GET("exercisecategory/") suspend fun categories(): Page<CategoryDto>

    @GET("exerciseinfo/")
    suspend fun exercises(
        @Query("language") language: Int = ENGLISH,
        @Query("limit") limit: Int = PAGE,
        @Query("offset") offset: Int = 0,
        @Query("category") category: Int? = null,
    ): Page<ExerciseInfoDto>

    @GET("exerciseinfo/{id}/") suspend fun exercise(@Path("id") id: Int): ExerciseInfoDto

    @GET("exercise/search/")
    suspend fun search(@Query("term") term: String, @Query("language") language: String = "english"): SearchResponse
}
