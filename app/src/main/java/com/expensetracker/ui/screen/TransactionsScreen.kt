package com.expensetracker.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Transaction
import com.expensetracker.sms.parser.Categories
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.PeriodFilterRow
import com.expensetracker.ui.components.ScreenHeader
import com.expensetracker.ui.components.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class ActivityUiState(
    val period: SpendPeriod = SpendPeriod.MONTH,
    val rangeLabel: String = "",
    val transactions: List<Transaction> = emptyList(),
    val categoryFilter: String? = null,
    val bankFilter: String? = null,
    val availableBanks: List<String> = emptyList(),
) {
    val hasChipFilters: Boolean
        get() = categoryFilter != null || bankFilter != null

    val activeFilterCount: Int
        get() = listOfNotNull(categoryFilter, bankFilter).size
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val queryFlow = MutableStateFlow<String?>(null)
    private val periodFlow = MutableStateFlow(SpendPeriod.MONTH)
    private val categoryFilterFlow = MutableStateFlow<String?>(null)
    private val bankFilterFlow = MutableStateFlow<String?>(null)

    val state: StateFlow<ActivityUiState> = combine(
        queryFlow,
        periodFlow,
        categoryFilterFlow,
        bankFilterFlow,
        dateBoundaryFlow(clock),
    ) { query, period, category, bank, _ ->
        ActivityFilters(query, period, category, bank)
    }
        .flatMapLatest { filters ->
            val window = DashboardRanges.forPeriod(filters.period, clock)
            repository.searchBetween(filters.query, window.fromInclusive, window.toExclusive)
                .map { list ->
                    val banks = list.mapNotNull { it.bank?.takeIf(String::isNotBlank) }
                        .distinct()
                        .sorted()
                    val filtered = list.filter { tx ->
                        (filters.category == null || tx.category == filters.category) &&
                            (filters.bank == null || tx.bank == filters.bank)
                    }
                    ActivityUiState(
                        period = filters.period,
                        rangeLabel = window.labelRange,
                        transactions = filtered,
                        categoryFilter = filters.category,
                        bankFilter = filters.bank,
                        availableBanks = banks,
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun setQuery(q: String) {
        queryFlow.value = q.takeIf { it.isNotBlank() }
    }

    fun setPeriod(period: SpendPeriod) {
        periodFlow.value = period
    }

    fun setCategoryFilter(category: String?) {
        categoryFilterFlow.value = category
    }

    fun setBankFilter(bank: String?) {
        bankFilterFlow.value = bank
    }

    fun clearChipFilters() {
        categoryFilterFlow.value = null
        bankFilterFlow.value = null
    }

    fun deleteSelected(ids: Collection<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteIds(ids)
            onDone()
        }
    }

    private data class ActivityFilters(
        val query: String?,
        val period: SpendPeriod,
        val category: String?,
        val bank: String?,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessageTemplate = stringResource(R.string.activity_copied_sms)
    val noSmsMessage = stringResource(R.string.activity_no_sms_to_copy)
    val deletedMessageTemplate = stringResource(R.string.activity_deleted)

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    val selectMode = selectedIds.isNotEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.transactions) {
        val visibleIds = state.transactions.mapTo(mutableSetOf()) { it.id }
        val pruned = selectedIds.intersect(visibleIds)
        if (pruned != selectedIds) selectedIds = pruned
    }

    fun toggleSelected(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun copySelected() {
        val selectedBodies = state.transactions
            .filter { it.id in selectedIds }
            .mapNotNull { it.rawSms?.takeIf { body -> body.isNotBlank() } }
        if (selectedBodies.isNotEmpty()) {
            clipboard.setText(AnnotatedString(selectedBodies.joinToString("\n\n")))
            scope.launch {
                snackbarHostState.showSnackbar(copiedMessageTemplate.format(selectedBodies.size))
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar(noSmsMessage) }
        }
        selectedIds = emptySet()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.bulk_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.bulk_delete_confirm_body, selectedIds.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds.toList()
                        confirmDelete = false
                        viewModel.deleteSelected(ids) {
                            scope.launch {
                                snackbarHostState.showSnackbar(deletedMessageTemplate.format(ids.size))
                            }
                            selectedIds = emptySet()
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.action_confirm_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showFiltersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            ActivityFiltersSheet(
                categoryFilter = state.categoryFilter,
                bankFilter = state.bankFilter,
                availableBanks = state.availableBanks,
                onCategory = viewModel::setCategoryFilter,
                onBank = viewModel::setBankFilter,
                onClear = viewModel::clearChipFilters,
                onDone = { showFiltersSheet = false },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectMode) {
                FloatingActionButton(
                    onClick = onAddTransaction,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_transaction_fab_a11y),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (selectMode) {
                SelectionToolbar(
                    count = selectedIds.size,
                    onCancel = { selectedIds = emptySet() },
                    onDelete = { confirmDelete = true },
                    onCopy = ::copySelected,
                )
            } else {
                ScreenHeader(
                    title = stringResource(R.string.tab_transactions),
                    subtitle = if (state.rangeLabel.isNotBlank()) {
                        state.rangeLabel
                    } else {
                        stringResource(R.string.transactions_subtitle)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search_hint),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(
                            onClick = {
                                query = ""
                                viewModel.setQuery("")
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.search_clear_a11y),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.height(12.dp))
            PeriodFilterRow(
                selected = state.period,
                onSelect = viewModel::setPeriod,
            )
            Spacer(Modifier.height(10.dp))
            ActivityFilterBar(
                state = state,
                resultCount = state.transactions.size,
                onOpenFilters = { showFiltersSheet = true },
                onClearCategory = { viewModel.setCategoryFilter(null) },
                onClearBank = { viewModel.setBankFilter(null) },
                onClearAll = viewModel::clearChipFilters,
            )
            Spacer(Modifier.height(12.dp))
            when {
                state.transactions.isEmpty() && query.isNotBlank() -> {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body),
                    )
                }
                state.transactions.isEmpty() && state.hasChipFilters -> {
                    EmptyState(
                        icon = Icons.Outlined.FilterListOff,
                        title = stringResource(R.string.empty_filters_title),
                        body = stringResource(R.string.empty_filters_body),
                        actionLabel = stringResource(R.string.empty_filters_action),
                        onAction = viewModel::clearChipFilters,
                    )
                }
                state.transactions.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.empty_transactions_title),
                        body = stringResource(R.string.empty_period_transactions),
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(state.transactions, key = { it.id }) { tx ->
                            TransactionListItem(
                                tx,
                                onClick = {
                                    if (selectMode) toggleSelected(tx.id) else onOpenTransaction(tx.id)
                                },
                                selectMode = selectMode,
                                selected = tx.id in selectedIds,
                                onLongClick = { toggleSelected(tx.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.selection_count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_cancel),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_copy_sms),
            )
        }
    }
}

@Composable
private fun ActivityFilterBar(
    state: ActivityUiState,
    resultCount: Int,
    onOpenFilters: () -> Unit,
    onClearCategory: () -> Unit,
    onClearBank: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val filtersLabel = if (state.activeFilterCount > 0) {
                stringResource(R.string.filters_button_active, state.activeFilterCount)
            } else {
                stringResource(R.string.filters_button)
            }
            Surface(
                onClick = onOpenFilters,
                shape = RoundedCornerShape(12.dp),
                color = if (state.hasChipFilters) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (state.hasChipFilters) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (state.hasChipFilters) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp),
                    )
                    Text(
                        filtersLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                if (state.hasChipFilters) {
                    stringResource(R.string.activity_result_count_filtered, resultCount)
                } else {
                    stringResource(R.string.activity_result_count, resultCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.hasChipFilters) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.categoryFilter?.let { cat ->
                    ActiveFilterChip(
                        label = cat,
                        onClear = onClearCategory,
                    )
                }
                state.bankFilter?.let { bank ->
                    ActiveFilterChip(
                        label = bank,
                        onClear = onClearBank,
                    )
                }
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.filters_clear))
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onClear: () -> Unit,
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.filter_chip_remove_a11y, label),
                modifier = Modifier.height(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityFiltersSheet(
    categoryFilter: String?,
    bankFilter: String?,
    availableBanks: List<String>,
    onCategory: (String?) -> Unit,
    onBank: (String?) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.filters_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onClear,
                enabled = categoryFilter != null || bankFilter != null,
            ) {
                Text(stringResource(R.string.filters_clear))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.filter_category),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { onCategory(null) },
                label = { Text(stringResource(R.string.filter_all_categories)) },
            )
            Categories.defaults.forEach { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { onCategory(if (categoryFilter == cat) null else cat) },
                    label = { Text(cat) },
                )
            }
        }
        if (availableBanks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.filter_bank),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = bankFilter == null,
                    onClick = { onBank(null) },
                    label = { Text(stringResource(R.string.filter_all_banks)) },
                )
                availableBanks.forEach { bank ->
                    FilterChip(
                        selected = bankFilter == bank,
                        onClick = { onBank(if (bankFilter == bank) null else bank) },
                        label = { Text(bank) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                stringResource(R.string.filters_apply),
                modifier = Modifier.padding(vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
