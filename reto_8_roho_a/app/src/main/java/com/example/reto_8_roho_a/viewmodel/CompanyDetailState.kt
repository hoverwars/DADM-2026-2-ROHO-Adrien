package com.example.reto_8_roho_a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.data.CompanyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la ficha de una empresa (lectura + eliminación). */
class CompanyDetailState(application: Application) : AndroidViewModel(application) {
    private val repository = CompanyRepository(application)
    private var companyId = Company.NO_ID

    private val _company = MutableStateFlow<Company?>(null)
    private val _loading = MutableStateFlow(true)
    private val _showDeleteDialog = MutableStateFlow(false)

    val company: StateFlow<Company?> = _company.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    fun setCompanyId(id: Long) {
        companyId = id
    }

    /** Se llama en onResume: la empresa pudo ser modificada en el formulario. */
    fun refresh() {
        viewModelScope.launch {
            _company.value = repository.getById(companyId)
            _loading.value = false
        }
    }

    fun askDelete() {
        _showDeleteDialog.value = true
    }

    fun dismissDelete() {
        _showDeleteDialog.value = false
    }

    /** Borra la empresa tras la confirmación del usuario; [onDeleted] recibe el nombre borrado. */
    fun confirmDelete(onDeleted: (String) -> Unit) {
        val current = _company.value ?: return
        _showDeleteDialog.value = false
        viewModelScope.launch {
            if (repository.delete(current.id)) onDeleted(current.name)
        }
    }
}
