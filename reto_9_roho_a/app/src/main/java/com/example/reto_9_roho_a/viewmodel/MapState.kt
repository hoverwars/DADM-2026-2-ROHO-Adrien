package com.example.reto_9_roho_a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_9_roho_a.data.GeocodingRepository
import com.example.reto_9_roho_a.data.Poi
import com.example.reto_9_roho_a.data.PoiCategory
import com.example.reto_9_roho_a.data.PoiRepository
import com.example.reto_9_roho_a.data.SettingsStore
import com.example.reto_9_roho_a.location.LocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Origen del centro de búsqueda. */
enum class CenterSource { GPS, ADDRESS }

data class MapUiState(
    val center: Pair<Double, Double>? = null,
    val source: CenterSource = CenterSource.GPS,
    val address: String = "",
    val radiusKm: Int = SettingsStore.DEFAULT_RADIUS_KM,
    val activeCategories: Set<PoiCategory> = PoiCategory.entries.toSet(),
    val pois: List<Poi> = emptyList(),
    val selectedPoiId: Long? = null,
    val loading: Boolean = false,
    val searched: Boolean = false,
    /** Mensaje de error de un solo uso; la UI lo muestra y llama a [MapState.consumeMessage]. */
    val message: String? = null
) {
    /** POIs visibles: el filtro de categorías del mapa se aplica sin repetir la consulta. */
    val visiblePois: List<Poi> get() = pois.filter { it.category in activeCategories }
}

class MapState(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val poiRepository = PoiRepository()
    private val geocoding = GeocodingRepository(application)
    private val locationProvider = LocationProvider(application)

    private val _state = MutableStateFlow(
        MapUiState(radiusKm = settings.radiusKm, activeCategories = settings.categories)
    )
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var appliedRadius = settings.radiusKm
    private var appliedCategories = settings.categories

    fun isLocationEnabled() = locationProvider.isLocationEnabled()

    fun onAddressChange(text: String) = _state.update { it.copy(address = text) }

    fun selectPoi(id: Long?) = _state.update { it.copy(selectedPoiId = id) }

    /** Filtro rápido de los chips del mapa: solo oculta o muestra, sin nueva consulta de red. */
    fun toggleCategory(category: PoiCategory) = _state.update {
        val next = if (category in it.activeCategories) it.activeCategories - category else it.activeCategories + category
        it.copy(activeCategories = next, selectedPoiId = null)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    /** Se llama en onResume: el radio o las categorías pueden haber cambiado en Ajustes. */
    fun reloadSettings() {
        val radius = settings.radiusKm
        val categories = settings.categories
        if (radius == appliedRadius && categories == appliedCategories) return
        appliedRadius = radius
        appliedCategories = categories
        _state.update { it.copy(radiusKm = radius, activeCategories = categories) }
        if (_state.value.center != null) loadPois()
    }

    /** Centra la búsqueda en la posición GPS (el permiso ya debe estar concedido). */
    fun useMyLocation() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, source = CenterSource.GPS) }
            val position = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationProvider.currentLocation() }
            if (position == null) {
                _state.update {
                    it.copy(loading = false, message = "No se pudo obtener tu ubicación. Revisa que el GPS esté activo.")
                }
                return@launch
            }
            _state.update { it.copy(center = position, selectedPoiId = null) }
            fetchPois(position)
        }
    }

    /** Geocodifica la dirección escrita y busca alrededor de ella. */
    fun searchAddress() {
        val text = _state.value.address
        if (text.isBlank()) {
            showMessage("Escribe una dirección.")
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, source = CenterSource.ADDRESS) }
            val position = geocoding.geocode(text)
            if (position == null) {
                _state.update { it.copy(loading = false, message = "No se encontró la dirección «$text».") }
                return@launch
            }
            _state.update { it.copy(center = position, selectedPoiId = null) }
            fetchPois(position)
        }
    }

    /** Repite la búsqueda alrededor del centro actual (por ejemplo, tras cambiar el radio). */
    fun loadPois() {
        val center = _state.value.center ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            fetchPois(center)
        }
    }

    private suspend fun fetchPois(center: Pair<Double, Double>) {
        val current = _state.value
        try {
            val pois = poiRepository.nearby(center.first, center.second, current.radiusKm, settings.categories)
            _state.update { it.copy(pois = pois, loading = false, searched = true) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(loading = false, message = "No se pudieron cargar los puntos de interés. Revisa tu conexión.")
            }
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 15_000L
    }
}
