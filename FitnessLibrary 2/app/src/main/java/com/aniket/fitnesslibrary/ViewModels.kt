package com.aniket.fitnesslibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExerciseListViewModel(private val repo: Repository) : ViewModel() {
    data class UiState(
        val items: List<Exercise> = emptyList(), val categories: List<CategoryDto> = emptyList(),
        val selectedCategory: Int? = null, val query: String = "",
        val loading: Boolean = false, val endReached: Boolean = false, val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var offset = 0
    private var job: Job? = null

    init {
        viewModelScope.launch { runCatching { repo.categories() }.onSuccess { c -> _state.update { it.copy(categories = c) } } }
        reload()
    }

    fun selectCategory(id: Int?) { _state.update { it.copy(selectedCategory = id, query = "") }; reload() }
    fun setQuery(q: String) { _state.update { it.copy(query = q) }; reload(debounce = true) }

    fun reload(debounce: Boolean = false) {
        job?.cancel()
        offset = 0
        _state.update { it.copy(items = emptyList(), loading = false, endReached = false, error = null) }
        job = viewModelScope.launch { if (debounce) delay(400); fetch() }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        job = viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val s = _state.value
            if (s.query.isNotBlank()) {
                val r = repo.search(s.query.trim())
                _state.update { it.copy(items = r, endReached = true, loading = false) }
            } else {
                val items = repo.featured(s.selectedCategory)
                _state.update { it.copy(items = items, endReached = true, loading = false) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Network error") }
        }
    }
}

class ExerciseDetailViewModel(private val repo: Repository, private val id: Int) : ViewModel() {
    data class UiState(val loading: Boolean = true, val exercise: Exercise? = null, val error: String? = null)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    val plans = repo.plans()

    init { load() }

    fun load() {
        _state.value = UiState()
        viewModelScope.launch {
            try { _state.value = UiState(loading = false, exercise = repo.exercise(id)) }
            catch (e: Exception) { _state.value = UiState(loading = false, error = e.message ?: "Network error") }
        }
    }

    fun addToPlan(planId: Long) = viewModelScope.launch { _state.value.exercise?.let { repo.addToPlan(planId, it) } }

    fun createPlanAndAdd(name: String) = viewModelScope.launch {
        val pid = repo.createPlan(name)
        _state.value.exercise?.let { repo.addToPlan(pid, it) }
    }
}
