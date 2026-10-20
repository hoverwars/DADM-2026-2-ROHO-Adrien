package com.example.reto_8_roho_a.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.ui.components.color
import com.example.reto_8_roho_a.ui.components.icon
import com.example.reto_8_roho_a.ui.theme.RETO_8_ROHO_ATheme
import com.example.reto_8_roho_a.viewmodel.CompanyForm
import com.example.reto_8_roho_a.viewmodel.CompanyFormErrors
import com.example.reto_8_roho_a.viewmodel.CompanyFormState

/** Formulario para crear (sin id) o editar (con id) una empresa. */
class CompanyFormActivity : ComponentActivity() {
    private val viewModel: CompanyFormState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.init(intent.getLongExtra(EXTRA_COMPANY_ID, Company.NO_ID))

        setContent {
            RETO_8_ROHO_ATheme {
                CompanyFormScreen(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onSaved = { name ->
                        val action = if (viewModel.isEditing) "actualizada" else "registrada"
                        Toast.makeText(this, "«$name» $action", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_COMPANY_ID = "COMPANY_ID"

        /** Sin [companyId] = nueva empresa; con id = edición. */
        fun newIntent(context: Context, companyId: Long = Company.NO_ID): Intent =
            Intent(context, CompanyFormActivity::class.java).putExtra(EXTRA_COMPANY_ID, companyId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyFormScreen(viewModel: CompanyFormState, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val form by viewModel.form.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val savedName by viewModel.savedName.collectAsState()

    LaunchedEffect(savedName) {
        savedName?.let(onSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar empresa" else "Nueva empresa") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cancelar") }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !saving && !loading) {
                        Icon(Icons.Outlined.Check, contentDescription = "Guardar")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormFields(form, errors, viewModel)

            Spacer(Modifier.height(4.dp))
            Text("Clasificación *", style = MaterialTheme.typography.titleMedium)
            ClassificationSelector(
                selected = form.classifications,
                error = errors.classifications,
                onToggle = viewModel::toggleClassification
            )

            Text(
                "* Campos obligatorios",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { viewModel.save() },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormFields(form: CompanyForm, errors: CompanyFormErrors, viewModel: CompanyFormState) {
    FormField(
        value = form.name,
        onValueChange = viewModel::onNameChange,
        label = "Nombre de la empresa *",
        icon = Icons.Outlined.Badge,
        error = errors.name,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    )
    FormField(
        value = form.url,
        onValueChange = viewModel::onUrlChange,
        label = "Página web",
        placeholder = "https://www.empresa.com",
        icon = Icons.Outlined.Language,
        error = errors.url,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
    )
    FormField(
        value = form.phone,
        onValueChange = viewModel::onPhoneChange,
        label = "Teléfono de contacto",
        placeholder = "+57 601 555 0000",
        icon = Icons.Outlined.Phone,
        error = errors.phone,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
    )
    FormField(
        value = form.email,
        onValueChange = viewModel::onEmailChange,
        label = "Email de contacto",
        placeholder = "contacto@empresa.com",
        icon = Icons.Outlined.Email,
        error = errors.email,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
    )
    FormField(
        value = form.productsServices,
        onValueChange = viewModel::onProductsServicesChange,
        label = "Productos y servicios",
        icon = Icons.Outlined.Inventory2,
        error = null,
        singleLine = false,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    error: String?,
    keyboardOptions: KeyboardOptions,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = { Icon(icon, contentDescription = null) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = keyboardOptions
    )
}

/** Una tarjeta marcable por clasificación (se pueden elegir varias). */
@Composable
private fun ClassificationSelector(
    selected: Set<Classification>,
    error: String?,
    onToggle: (Classification) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Classification.entries.forEach { classification ->
            val checked = classification in selected
            OutlinedCard(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = if (checked) 2.dp else 1.dp,
                    color = when {
                        checked -> classification.color
                        error != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(classification) })
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(classification.icon, contentDescription = null, tint = classification.color)
                    Spacer(Modifier.width(12.dp))
                    Text(classification.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null, // el clic lo gestiona toda la fila (toggleable)
                        colors = CheckboxDefaults.colors(checkedColor = classification.color)
                    )
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
