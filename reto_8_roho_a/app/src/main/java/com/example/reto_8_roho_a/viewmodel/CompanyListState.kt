package com.example.reto_8_roho_a.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.data.CompanyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "CompanyListState"

/** Estado de la lista de empresas: filtros (nombre + clasificaciones) y resultados. */
class CompanyListState(application: Application) : AndroidViewModel(application) {
    private val repository = CompanyRepository(application)
    private var searchJob: Job? = null
    private var initialized = false

    private val _nameQuery = MutableStateFlow("")
    private val _selectedClassifications = MutableStateFlow<Set<Classification>>(emptySet())
    private val _companies = MutableStateFlow<List<Company>>(emptyList())
    private val _loading = MutableStateFlow(true)

    val nameQuery: StateFlow<String> = _nameQuery.asStateFlow()
    val selectedClassifications: StateFlow<Set<Classification>> = _selectedClassifications.asStateFlow()
    val companies: StateFlow<List<Company>> = _companies.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Empresa para la que se muestra el diálogo de confirmación de borrado (null = sin diálogo). */
    private val _pendingDelete = MutableStateFlow<Company?>(null)
    val pendingDelete: StateFlow<Company?> = _pendingDelete.asStateFlow()

    /** Filtro inicial recibido desde la pantalla de inicio (toque en una estadística). */
    fun initClassificationFilter(classification: Classification?) {
        if (initialized) return // el ViewModel sobrevive a la rotación: no pisar los filtros del usuario
        initialized = true
        if (classification != null) _selectedClassifications.value = setOf(classification)
    }

    fun askDelete(company: Company) {
        _pendingDelete.value = company
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    /** Borra la empresa tras la confirmación del usuario; [onDeleted] recibe el nombre borrado. */
    fun confirmDelete(onDeleted: (String) -> Unit) {
        val company = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            if (repository.delete(company.id)) {
                onDeleted(company.name)
                refresh()
            }
        }
    }

    fun setNameQuery(query: String) {
        _nameQuery.value = query
        refresh()
    }

    fun toggleClassification(classification: Classification) {
        _selectedClassifications.value = _selectedClassifications.value.let {
            if (classification in it) it - classification else it + classification
        }
        refresh()
    }

    fun clearFilters() {
        _nameQuery.value = ""
        _selectedClassifications.value = emptySet()
        refresh()
    }

    /** Vuelve a consultar SQLite con los filtros actuales (cancela la búsqueda anterior si sigue en curso). */
    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val result = repository.search(_nameQuery.value, _selectedClassifications.value)
            Log.d(TAG, "Búsqueda '${_nameQuery.value}' ${_selectedClassifications.value}: ${result.size} resultados")
            _companies.value = result
            _loading.value = false
        }
    }
}
