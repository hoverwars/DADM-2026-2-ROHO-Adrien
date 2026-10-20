package com.example.reto_8_roho_a.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.data.Company
import com.example.reto_8_roho_a.ui.components.ClassificationBadges
import com.example.reto_8_roho_a.ui.components.CompanyAvatar
import com.example.reto_8_roho_a.ui.components.ConfirmDeleteDialog
import com.example.reto_8_roho_a.ui.components.color
import com.example.reto_8_roho_a.ui.components.icon
import com.example.reto_8_roho_a.ui.theme.RETO_8_ROHO_ATheme
import com.example.reto_8_roho_a.viewmodel.CompanyListState
import kotlinx.coroutines.launch

/** Lista de empresas con búsqueda por nombre y filtro por clasificación. */
class CompanyListActivity : ComponentActivity() {
    private val viewModel: CompanyListState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialFilter = intent.getStringExtra(EXTRA_CLASSIFICATION)?.let { Classification.valueOf(it) }
        viewModel.initClassificationFilter(initialFilter)

        setContent {
            RETO_8_ROHO_ATheme {
                CompanyListScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver del detalle o del formulario, la lista puede haber cambiado.
        viewModel.refresh()
    }

    companion object {
        private const val EXTRA_CLASSIFICATION = "CLASSIFICATION"

        fun newIntent(context: Context, classification: Classification? = null): Intent =
            Intent(context, CompanyListActivity::class.java).apply {
                classification?.let { putExtra(EXTRA_CLASSIFICATION, it.name) }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyListScreen(viewModel: CompanyListState, onBack: () -> Unit) {
    val context = LocalContext.current
    val nameQuery by viewModel.nameQuery.collectAsState()
    val selected by viewModel.selectedClassifications.collectAsState()
    val companies by viewModel.companies.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hasFilters = nameQuery.isNotBlank() || selected.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Directorio de empresas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { context.startActivity(CompanyFormActivity.newIntent(context)) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nueva empresa") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(query = nameQuery, onQueryChange = viewModel::setNameQuery)
            ClassificationFilters(selected = selected, onToggle = viewModel::toggleClassification)
            ResultsHeader(count = companies.size, hasFilters = hasFilters, onClear = viewModel::clearFilters)

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                companies.isEmpty() -> EmptyState(hasFilters = hasFilters)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(companies, key = { it.id }) { company ->
                        CompanyCard(
                            company = company,
                            onClick = { context.startActivity(CompanyDetailActivity.newIntent(context, company.id)) },
                            onEdit = { context.startActivity(CompanyFormActivity.newIntent(context, company.id)) },
                            onDelete = { viewModel.askDelete(company) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { company ->
        ConfirmDeleteDialog(
            companyName = company.name,
            onConfirm = {
                viewModel.confirmDelete { name ->
                    scope.launch { snackbarHostState.showSnackbar("«$name» eliminada") }
                }
            },
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar por nombre") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

@Composable
private fun ClassificationFilters(selected: Set<Classification>, onToggle: (Classification) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Classification.entries.forEach { classification ->
            val isSelected = classification in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(classification) },
                label = { Text(classification.label) },
                leadingIcon = {
                    Icon(
                        classification.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else classification.color
                    )
                }
            )
        }
    }
}

@Composable
private fun ResultsHeader(count: Int, hasFilters: Boolean, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count == 1) "1 empresa" else "$count empresas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (hasFilters) {
            TextButton(onClick = onClear) { Text("Limpiar filtros") }
        }
    }
}

@Composable
private fun CompanyCard(
    company: Company,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            CompanyAvatar(name = company.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (company.productsServices.isNotBlank()) {
                    Text(
                        text = company.productsServices,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                ClassificationBadges(company.classifications)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasFilters) "Ninguna empresa coincide con los filtros" else "Aún no hay empresas registradas",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasFilters) "Prueba con otro nombre o clasificación." else "Toca «Nueva empresa» para agregar la primera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
