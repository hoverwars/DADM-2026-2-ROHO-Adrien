package com.example.reto_8_roho_a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.data.CompanyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estadísticas de la pantalla de inicio. La clave null corresponde al total de empresas. */
class HomeState(application: Application) : AndroidViewModel(application) {
    private val repository = CompanyRepository(application)

    private val _counts = MutableStateFlow<Map<Classification?, Int>>(emptyMap())
    val counts: StateFlow<Map<Classification?, Int>> = _counts.asStateFlow()

    /** Se llama en onResume: los datos pueden haber cambiado en otra pantalla. */
    fun refresh() {
        viewModelScope.launch { _counts.value = repository.countByClassification() }
    }
}
