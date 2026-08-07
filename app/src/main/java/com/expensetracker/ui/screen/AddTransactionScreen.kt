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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.ui.components.SurfaceCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    fun save(
        amountText: String,
        type: TransactionType,
        merchant: String,
        category: String,
        date: LocalDate,
        notes: String,
        onDone: () -> Unit,
    ) {
        val amount = runCatching { Money.ofRupees(amountText.trim()) }.getOrNull() ?: return
        if (amount.amount.signum() <= 0) return
        val cat = category.trim().ifEmpty { Categories.OTHERS }
        viewModelScope.launch {
            val zone = clock.zone
            val tx = Transaction(
                amount = amount,
                type = type,
                merchant = merchant.trim().takeIf { it.isNotEmpty() },
                category = cat,
                bank = null,
                accountLast4 = null,
                cardLast4 = null,
                upiId = null,
                referenceNumber = null,
                balance = null,
                paymentMode = PaymentMode.CASH,
                timestamp = date.atStartOfDay(zone).toInstant(),
                sender = null,
                rawSms = null,
                narration = null,
                notes = notes.trim().takeIf { it.isNotEmpty() },
                dedupeHash = "manual-" + UUID.randomUUID(),
                manuallyEdited = true,
            )
            repository.insertManual(tx)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.OTHERS) }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }

    val amountValid = remember(amountText) {
        runCatching {
            val m = Money.ofRupees(amountText.trim())
            m.amount.signum() > 0
        }.getOrDefault(false)
    }
    val dateParsed = remember(dateText) {
        runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    }
    val canSave = amountValid && dateParsed != null && category.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_transaction_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SurfaceCard {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.label_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    prefix = { Text("₹") },
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.label_type), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == TransactionType.DEBIT,
                        onClick = { type = TransactionType.DEBIT },
                        label = { Text(stringResource(R.string.type_debit)) },
                    )
                    FilterChip(
                        selected = type == TransactionType.CREDIT,
                        onClick = { type = TransactionType.CREDIT },
                        label = { Text(stringResource(R.string.type_credit)) },
                    )
                }
            }

            SurfaceCard {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.label_merchant)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.label_date)) },
                    supportingText = { Text(stringResource(R.string.label_date_hint)) },
                    singleLine = true,
                    isError = dateParsed == null && dateText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
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

            Button(
                onClick = {
                    val date = dateParsed ?: return@Button
                    viewModel.save(
                        amountText = amountText,
                        type = type,
                        merchant = merchant,
                        category = category,
                        date = date,
                        notes = notes,
                        onDone = onBack,
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
