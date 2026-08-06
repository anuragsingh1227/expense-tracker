package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.MerchantCatalog
import com.expensetracker.ui.components.LoadingBlock
import com.expensetracker.ui.components.MetaRow
import com.expensetracker.ui.components.StatusPill
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.theme.ExpenseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val merchantCatalog: MerchantCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow<Transaction?>(null)
    val state: StateFlow<Transaction?> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch { _state.value = repository.find(id) }
    }

    fun save(updated: Transaction, rememberForMerchant: Boolean) {
        viewModelScope.launch {
            repository.update(updated.copy(manuallyEdited = true))
            if (rememberForMerchant) {
                val key = updated.merchant?.takeIf { it.isNotBlank() }
                    ?: updated.narration?.takeIf { it.isNotBlank() }
                if (key != null) {
                    merchantCatalog.remember(key, updated.category)
                }
            }
            _state.value = repository.find(updated.id)
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    val tx: Transaction? by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transaction_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = tx
        if (current == null) {
            LoadingBlock(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        var category by remember(current.id) { mutableStateOf(current.category) }
        var notes by remember(current.id) { mutableStateOf(current.notes.orEmpty()) }
        var rememberMerchant by remember(current.id) { mutableStateOf(true) }
        val isCredit = current.type == TransactionType.CREDIT

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SurfaceCard {
                StatusPill(
                    text = if (isCredit) stringResource(R.string.type_credit) else stringResource(R.string.type_debit),
                    positive = isCredit,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    current.amount.formatInr(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) ExpenseColors.Income else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    current.merchant ?: current.category,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            SurfaceCard {
                MetaRow(stringResource(R.string.label_bank), current.bank ?: "—")
                Spacer(Modifier.height(12.dp))
                MetaRow(stringResource(R.string.label_payment), current.paymentMode.name.replace('_', ' '))
                Spacer(Modifier.height(12.dp))
                MetaRow(stringResource(R.string.label_reference), current.referenceNumber ?: "—")
                current.balance?.let {
                    Spacer(Modifier.height(12.dp))
                    MetaRow(stringResource(R.string.label_balance_after), it.formatInr())
                }
            }

            SurfaceCard {
                Text(stringResource(R.string.label_raw_sms), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    current.rawSms ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(stringResource(R.string.label_category), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Categories.defaults.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option) },
                    )
                }
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.label_category_custom)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.label_notes)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = rememberMerchant,
                    onCheckedChange = { rememberMerchant = it },
                )
                Text(
                    stringResource(R.string.remember_merchant_category),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        viewModel.save(
                            current.copy(
                                category = category.trim().ifEmpty { current.category },
                                notes = notes.trim().ifEmpty { null },
                            ),
                            rememberForMerchant = rememberMerchant,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(
                    onClick = { viewModel.delete(current.id, onBack) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.action_delete)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
