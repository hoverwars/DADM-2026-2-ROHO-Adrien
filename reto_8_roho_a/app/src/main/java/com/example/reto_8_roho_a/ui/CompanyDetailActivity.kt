package com.example.reto_8_roho_a.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.ui.components.ClassificationBadges
import com.example.reto_8_roho_a.ui.components.CompanyAvatar
import com.example.reto_8_roho_a.ui.components.ConfirmDeleteDialog
import com.example.reto_8_roho_a.ui.theme.RETO_8_ROHO_ATheme
import com.example.reto_8_roho_a.viewmodel.CompanyDetailState

/** Ficha de una empresa: datos de contacto accionables, edición y eliminación. */
class CompanyDetailActivity : ComponentActivity() {
    private val viewModel: CompanyDetailState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.setCompanyId(intent.getLongExtra(EXTRA_COMPANY_ID, Company.NO_ID))

        setContent {
            RETO_8_ROHO_ATheme {
                CompanyDetailScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onDeleted = { name ->
                        Toast.makeText(this, "«$name» eliminada", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarga tras volver del formulario de edición.
        viewModel.refresh()
    }

    companion object {
        private const val EXTRA_COMPANY_ID = "COMPANY_ID"

        fun newIntent(context: Context, companyId: Long): Intent =
            Intent(context, CompanyDetailActivity::class.java).putExtra(EXTRA_COMPANY_ID, companyId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyDetailScreen(viewModel: CompanyDetailState, onBack: () -> Unit, onDeleted: (String) -> Unit) {
    val context = LocalContext.current
    val company by viewModel.company.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()

    // La empresa ya no existe (borrada desde otra pantalla): se cierra la ficha.
    LaunchedEffect(loading, company) {
        if (!loading && company == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    company?.let { c ->
                        IconButton(onClick = { context.startActivity(CompanyFormActivity.newIntent(context, c.id)) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = viewModel::askDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val c = company
        if (c == null) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(c)
            ContactCard(c)
            SectionCard(title = "Productos y servicios", icon = Icons.Outlined.Inventory2) {
                Text(
                    text = c.productsServices.ifBlank { "Sin información" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (c.productsServices.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::askDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar")
                }
                Button(
                    onClick = { context.startActivity(CompanyFormActivity.newIntent(context, c.id)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Editar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                companyName = c.name,
                onConfirm = { viewModel.confirmDelete(onDeleted) },
                onDismiss = viewModel::dismissDelete
            )
        }
    }
}

@Composable
private fun Header(company: Company) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompanyAvatar(name = company.name, size = 88.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = company.name,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        ClassificationBadges(company.classifications)
    }
}

/** Web, teléfono y email: cada fila abre la app correspondiente (navegador, marcador, correo). */
@Composable
private fun ContactCard(company: Company) {
    val context = LocalContext.current
    SectionCard(title = "Contacto", icon = null) {
        ContactRow(Icons.Outlined.Language, "Página web", company.url) { openWebsite(context, company.url) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ContactRow(Icons.Outlined.Phone, "Teléfono", company.phone) { dialPhone(context, company.phone) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ContactRow(Icons.Outlined.Email, "Email", company.email) { sendEmail(context, company.email) }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    val enabled = value.isNotBlank()
    ListItem(
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        overlineContent = { Text(label) },
        headlineContent = {
            Text(
                text = value.ifBlank { "No registrado" },
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Abrir",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun SectionCard(title: String, icon: ImageVector?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}
