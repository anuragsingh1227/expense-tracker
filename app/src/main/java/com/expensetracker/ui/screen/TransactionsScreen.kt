package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Transaction
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val queryFlow = MutableStateFlow<String?>(null)
    private val periodFlow = MutableStateFlow(SpendPeriod.MONTH)

    val state: StateFlow<ActivityUiState> = combine(
        queryFlow,
        periodFlow,
        dateBoundaryFlow(clock),
    ) { query, period, _ -> query to period }
        .flatMapLatest { (query, period) ->
            val window = DashboardRanges.forPeriod(period, clock)
            repository.searchBetween(query, window.fromInclusive, window.toExclusive)
                .map { list ->
                    ActivityUiState(
                        period = period,
                        rangeLabel = window.labelRange,
                        transactions = list,
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
}

@Composable
fun TransactionsScreen(
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessageTemplate = stringResource(R.string.activity_copied_sms)
    val noSmsMessage = stringResource(R.string.activity_no_sms_to_copy)

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectMode = selectedIds.isNotEmpty()

    // If the visible list changes (period/search change, or a purge/reconcile ran)
    // while some selected rows are no longer shown, drop them so the "N selected"
    // count and Copy action only ever reflect what's actually still selectable.
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

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
