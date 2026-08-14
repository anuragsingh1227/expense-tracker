package com.expensetracker.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.insights.PeerBalance
import com.expensetracker.domain.insights.SplitLedger
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.sms.parser.Categories
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.PeriodFilterRow
import com.expensetracker.ui.components.ScreenHeader
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.TransactionListItem
import com.expensetracker.ui.components.maskableFormatInr
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
    val peerBalances: List<PeerBalance> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsDao: SettingsDao,
    private val clock: Clock,
) : ViewModel() {

    private val queryFlow = MutableStateFlow<String?>(null)
    private val periodFlow = MutableStateFlow(SpendPeriod.MONTH)
    private val categoryFilterFlow = MutableStateFlow<String?>(null)
    private val bankFilterFlow = MutableStateFlow<String?>(null)
    private val billingDayFlow = settingsDao.observe(AppSettings.CC_BILLING_CYCLE_START_DAY)
        .map { it?.toIntOrNull()?.coerceIn(1, 28) ?: 1 }

    val state: StateFlow<ActivityUiState> = combine(
        queryFlow,
        periodFlow,
        categoryFilterFlow,
        bankFilterFlow,
        billingDayFlow,
    ) { query, period, category, bank, billingDay ->
        ActivityFilters(query, period, category, bank, billingDay)
    }
        .combine(dateBoundaryFlow(clock)) { filters, _ -> filters }
        .flatMapLatest { filters ->
            val window = DashboardRanges.forPeriod(
                filters.period,
                clock,
                billingCycleStartDay = filters.billingDay,
            )
            combine(
                repository.searchBetween(filters.query, window.fromInclusive, window.toExclusive),
                repository.observeAll().map { SplitLedger.balances(it) },
            ) { list, debts ->
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
                    peerBalances = debts,
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
        val billingDay: Int,
    )
}

private val FILTER_CATEGORIES = listOf(
    Categories.FOOD,
    Categories.GROCERIES,
    Categories.SHOPPING,
    Categories.TRAVEL,
    Categories.TRANSPORT,
    Categories.UTILITIES,
    Categories.TRANSFER,
    Categories.OTHERS,
)

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
    val selectMode = selectedIds.isNotEmpty()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.selection_count, selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedIds = emptySet() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                        Button(onClick = ::copySelected) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_copy_sms))
                        }
                    }
                }
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
            Spacer(Modifier.height(14.dp))
            PeriodFilterRow(
                selected = state.period,
                onSelect = viewModel::setPeriod,
            )
            if (state.peerBalances.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DebtsCard(balances = state.peerBalances)
            }
            Spacer(Modifier.height(10.dp))
            CategoryFilterRow(
                selected = state.categoryFilter,
                onSelect = viewModel::setCategoryFilter,
            )
            if (state.availableBanks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                BankFilterRow(
                    banks = state.availableBanks,
                    selected = state.bankFilter,
                    onSelect = viewModel::setBankFilter,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search_hint),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.height(16.dp))
            when {
                state.transactions.isEmpty() && query.isNotBlank() -> {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body),
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun DebtsCard(balances: List<PeerBalance>) {
    val owedToMe = SplitLedger.totalOwedToMe(balances)
    SurfaceCard {
        Text(
            stringResource(R.string.debts_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.debts_subtitle, owedToMe.maskableFormatInr()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        balances.forEach { row ->
            val label = if (row.owedToMe.amount.signum() >= 0) {
                stringResource(R.string.debts_owes_me, row.name, row.owedToMe.maskableFormatInr())
            } else {
                stringResource(
                    R.string.debts_i_owe,
                    row.name,
                    Money(row.owedToMe.amount.abs()).maskableFormatInr(),
                )
            }
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all_categories)) },
        )
        FILTER_CATEGORIES.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(if (selected == cat) null else cat) },
                label = { Text(cat) },
            )
        }
    }
}

@Composable
private fun BankFilterRow(
    banks: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all_banks)) },
        )
        banks.forEach { bank ->
            FilterChip(
                selected = selected == bank,
                onClick = { onSelect(if (selected == bank) null else bank) },
                label = { Text(bank) },
            )
        }
    }
}
