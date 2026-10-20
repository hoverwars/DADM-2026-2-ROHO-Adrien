package com.example.reto_8_roho_a.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.data.CompanyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Valores del formulario tal como los escribe el usuario (sin validar). */
data class CompanyForm(
    val name: String = "",
    val url: String = "",
    val phone: String = "",
    val email: String = "",
    val productsServices: String = "",
    val classifications: Set<Classification> = emptySet(),
)

/** Mensaje de error por campo; null = campo válido. */
data class CompanyFormErrors(
    val name: String? = null,
    val url: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val classifications: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, url, phone, email, classifications).any { it != null }
}

/** Estado del formulario de creación / edición de una empresa. */
class CompanyFormState(application: Application) : AndroidViewModel(application) {
    private val repository = CompanyRepository(application)
    private var companyId = Company.NO_ID
    private var initialized = false

    private val _form = MutableStateFlow(CompanyForm())
    private val _errors = MutableStateFlow(CompanyFormErrors())
    private val _loading = MutableStateFlow(false)
    private val _saving = MutableStateFlow(false)
    private val _savedName = MutableStateFlow<String?>(null)

    val form: StateFlow<CompanyForm> = _form.asStateFlow()
    val errors: StateFlow<CompanyFormErrors> = _errors.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Nombre de la empresa una vez guardada (null = aún no). Es un estado y no un callback para que
     * la Activity recreada tras una rotación durante el guardado también se cierre (y no se inserte dos veces).
     */
    val savedName: StateFlow<String?> = _savedName.asStateFlow()

    val isEditing: Boolean get() = companyId != Company.NO_ID

    /** Carga la empresa a editar (solo la primera vez: el ViewModel sobrevive a la rotación). */
    fun init(id: Long) {
        if (initialized) return
        initialized = true
        companyId = id
        if (!isEditing) return
        _loading.value = true
        viewModelScope.launch {
            repository.getById(id)?.let { c ->
                _form.value = CompanyForm(c.name, c.url, c.phone, c.email, c.productsServices, c.classifications)
            }
            _loading.value = false
        }
    }

    fun onNameChange(value: String) {
        _form.update { it.copy(name = value) }
        _errors.update { it.copy(name = null) }
    }

    fun onUrlChange(value: String) {
        _form.update { it.copy(url = value) }
        _errors.update { it.copy(url = null) }
    }

    fun onPhoneChange(value: String) {
        _form.update { it.copy(phone = value) }
        _errors.update { it.copy(phone = null) }
    }

    fun onEmailChange(value: String) {
        _form.update { it.copy(email = value) }
        _errors.update { it.copy(email = null) }
    }

    fun onProductsServicesChange(value: String) {
        _form.update { it.copy(productsServices = value) }
    }

    fun toggleClassification(classification: Classification) {
        _form.update {
            val set = it.classifications
            it.copy(classifications = if (classification in set) set - classification else set + classification)
        }
        _errors.update { it.copy(classifications = null) }
    }

    /**
     * Valida y guarda (INSERT si es nueva, UPDATE si se está editando).
     * Al terminar con éxito, [savedName] recibe el nombre de la empresa guardada.
     */
    fun save() {
        if (_saving.value || _savedName.value != null) return
        val f = _form.value.trimmed()
        val errors = validate(f)
        _errors.value = errors
        if (errors.hasErrors) return

        _saving.value = true
        viewModelScope.launch {
            val company = Company(
                id = companyId,
                name = f.name,
                url = f.url,
                phone = f.phone,
                email = f.email,
                productsServices = f.productsServices,
                classifications = f.classifications,
            )
            val ok = if (isEditing) repository.update(company) else repository.insert(company) != -1L
            _saving.value = false
            if (ok) _savedName.value = company.name
        }
    }

    private fun CompanyForm.trimmed() = copy(
        name = name.trim(),
        url = url.trim(),
        phone = phone.trim(),
        email = email.trim(),
        productsServices = productsServices.trim(),
    )

    /** Nombre y clasificación obligatorios; URL, teléfono y email opcionales pero con formato válido. */
    private fun validate(f: CompanyForm) = CompanyFormErrors(
        name = if (f.name.isEmpty()) "El nombre es obligatorio" else null,
        url = if (f.url.isNotEmpty() && !Patterns.WEB_URL.matcher(f.url).matches()) "URL no válida" else null,
        phone = if (f.phone.isNotEmpty() && !Patterns.PHONE.matcher(f.phone).matches()) "Teléfono no válido" else null,
        email = if (f.email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(f.email).matches()) "Email no válido" else null,
        classifications = if (f.classifications.isEmpty()) "Selecciona al menos una clasificación" else null,
    )
}
