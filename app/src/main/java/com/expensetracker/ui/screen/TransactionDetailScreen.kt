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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.HashtagParser
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.SplitShare
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.sms.parser.MerchantCatalog
import com.expensetracker.ui.components.LoadingBlock
import com.expensetracker.ui.components.MetaRow
import com.expensetracker.ui.components.StatusPill
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.maskableFormatInr
import com.expensetracker.ui.theme.ExpenseColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val merchantCatalog: MerchantCatalog,
    private val labelRuleCatalog: LabelRuleCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow<Transaction?>(null)
    val state: StateFlow<Transaction?> = _state.asStateFlow()

    private val _ruleSavedMessage = MutableStateFlow<String?>(null)
    val ruleSavedMessage: StateFlow<String?> = _ruleSavedMessage.asStateFlow()

    /** Increments on every successful save — UI shows a "Saved" snackbar when it changes. */
    private val _saveEvent = MutableStateFlow(0)
    val saveEvent: StateFlow<Int> = _saveEvent.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch { _state.value = repository.find(id) }
    }

    fun clearRuleMessage() {
        _ruleSavedMessage.value = null
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
            _saveEvent.value += 1
        }
    }

    fun createLabelRule(
        label: String,
        useSender: Boolean,
        senderContains: String,
        useBody: Boolean,
        bodyContains: String,
        useMerchant: Boolean,
        merchantContains: String,
        applyToThisTransaction: Boolean,
    ) {
        viewModelScope.launch {
            val current = _state.value ?: return@launch
            val created = labelRuleCatalog.create(
                label = label,
                senderContains = senderContains.takeIf { useSender },
                bodyContains = bodyContains.takeIf { useBody },
                merchantContains = merchantContains.takeIf { useMerchant },
            )
            if (created == null) {
                _ruleSavedMessage.value = "NEED_CRITERIA"
                return@launch
            }
            if (applyToThisTransaction) {
                repository.update(
                    current.copy(
                        category = created.label,
                        manuallyEdited = true,
                    ),
                )
                _state.value = repository.find(current.id)
            }
            _ruleSavedMessage.value = "OK"
        }
    }

    fun previewMatches(
        useSender: Boolean,
        senderContains: String,
        useBody: Boolean,
        bodyContains: String,
        useMerchant: Boolean,
        merchantContains: String,
    ): Boolean {
        val current = _state.value ?: return false
        val draft = LabelRuleEntity(
            label = "preview",
            senderContains = senderContains.trim().takeIf { useSender && it.isNotEmpty() }?.uppercase(),
            bodyContains = bodyContains.trim().takeIf { useBody && it.isNotEmpty() }?.uppercase(),
            merchantContains = merchantContains.trim().takeIf { useMerchant && it.isNotEmpty() }?.uppercase(),
        )
        if (draft.senderContains == null && draft.bodyContains == null && draft.merchantContains == null) {
            return false
        }
        return LabelRuleCatalog.matches(
            draft,
            current.sender.orEmpty().uppercase(),
            current.rawSms.orEmpty().uppercase(),
            current.merchant.orEmpty().uppercase(),
        )
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
    val ruleMessage by viewModel.ruleSavedMessage.collectAsState()
    val saveEvent by viewModel.saveEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.transaction_saved)
    var ruleNeedsCriteria by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(saveEvent) {
        if (saveEvent > 0) scope.launch { snackbarHostState.showSnackbar(savedMessage) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(transactionId, onBack)
                    },
                ) { Text(stringResource(R.string.action_confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = tx
        if (current == null) {
            LoadingBlock(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        var category by remember(current.id, current.category) { mutableStateOf(current.category) }
        var type by remember(current.id, current.type) { mutableStateOf(current.type) }
        var notes by remember(current.id) { mutableStateOf(current.notes.orEmpty()) }
        var selectedTags by remember(current.id) {
            mutableStateOf(HashtagParser.merge(current.notes, current.tags).toSet())
        }
        var splitOn by remember(current.id, current.isSplit) { mutableStateOf(current.isSplit) }
        var splitRows by remember(current.id) {
            mutableStateOf(
                current.splitShares.map { it.name to it.amountOwed.amount.stripTrailingZeros().toPlainString() }
                    .ifEmpty { listOf("" to "") },
            )
        }
        var amountText by remember(current.id, current.amount) {
            mutableStateOf(current.amount.amount.stripTrailingZeros().toPlainString())
        }
        var merchantText by remember(current.id, current.merchant) {
            mutableStateOf(current.merchant.orEmpty())
        }
        var dateText by remember(current.id, current.timestamp) {
            mutableStateOf(
                current.timestamp.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            )
        }
        var rememberMerchant by remember(current.id) { mutableStateOf(true) }

        var showLabelForm by remember(current.id) { mutableStateOf(false) }
        var labelName by remember(current.id) { mutableStateOf(current.category) }
        var useSender by remember(current.id) { mutableStateOf(!current.sender.isNullOrBlank()) }
        var senderContains by remember(current.id) {
            mutableStateOf(LabelRuleCatalog.suggestSenderContains(current.sender).orEmpty())
        }
        var useBody by remember(current.id) { mutableStateOf(true) }
        var bodyContains by remember(current.id) {
            mutableStateOf(
                LabelRuleCatalog.suggestBodyContains(
                    current.rawSms,
                    current.merchant,
                    current.narration,
                ).orEmpty(),
            )
        }
        var useMerchant by remember(current.id) {
            mutableStateOf(!current.merchant.isNullOrBlank())
        }
        var merchantContains by remember(current.id) {
            mutableStateOf(current.merchant.orEmpty())
        }
        var applyToThis by remember(current.id) { mutableStateOf(true) }

        LaunchedEffect(current.category) {
            category = current.category
            if (!showLabelForm) labelName = current.category
        }

        LaunchedEffect(ruleMessage) {
            when (ruleMessage) {
                "OK" -> {
                    category = labelName.trim().ifEmpty { category }
                    showLabelForm = false
                    ruleNeedsCriteria = false
                    viewModel.clearRuleMessage()
                }
                "NEED_CRITERIA" -> {
                    ruleNeedsCriteria = true
                    viewModel.clearRuleMessage()
                }
                else -> Unit
            }
        }

        val matchesPreview = viewModel.previewMatches(
            useSender, senderContains, useBody, bodyContains, useMerchant, merchantContains,
        )
        val isCredit = type == TransactionType.CREDIT

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
                    current.amount.maskableFormatInr(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) ExpenseColors.Income else ExpenseColors.Coral,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    current.merchant ?: current.category,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.label_amount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    prefix = { Text("₹") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text(stringResource(R.string.label_merchant)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.label_date)) },
                    supportingText = { Text(stringResource(R.string.label_date_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            SurfaceCard {
                MetaRow(stringResource(R.string.label_bank), current.bank ?: "—")
                Spacer(Modifier.height(12.dp))
                MetaRow(stringResource(R.string.label_sender), current.sender ?: "—")
                Spacer(Modifier.height(12.dp))
                MetaRow(stringResource(R.string.label_payment), friendlyPaymentMode(current.paymentMode))
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

            SurfaceCard {
                Text(
                    stringResource(R.string.label_rule_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.label_rule_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (!showLabelForm) {
                    OutlinedButton(
                        onClick = { showLabelForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.label_rule_create))
                    }
                } else {
                    OutlinedTextField(
                        value = labelName,
                        onValueChange = { labelName = it },
                        label = { Text(stringResource(R.string.label_rule_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    CriterionRow(
                        checked = useSender,
                        onCheckedChange = { useSender = it },
                        label = stringResource(R.string.label_rule_match_sender),
                        value = senderContains,
                        onValueChange = { senderContains = it },
                        enabled = useSender,
                    )
                    Spacer(Modifier.height(8.dp))
                    CriterionRow(
                        checked = useBody,
                        onCheckedChange = { useBody = it },
                        label = stringResource(R.string.label_rule_match_body),
                        value = bodyContains,
                        onValueChange = { bodyContains = it },
                        enabled = useBody,
                    )
                    Spacer(Modifier.height(8.dp))
                    CriterionRow(
                        checked = useMerchant,
                        onCheckedChange = { useMerchant = it },
                        label = stringResource(R.string.label_rule_match_merchant),
                        value = merchantContains,
                        onValueChange = { merchantContains = it },
                        enabled = useMerchant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = applyToThis,
                            onCheckedChange = { applyToThis = it },
                        )
                        Text(
                            stringResource(R.string.label_rule_apply_this),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        if (matchesPreview) {
                            stringResource(R.string.label_rule_matches_yes)
                        } else {
                            stringResource(R.string.label_rule_matches_no)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (matchesPreview) {
                            ExpenseColors.Income
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (ruleNeedsCriteria) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.label_rule_need_criteria),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                ruleNeedsCriteria = false
                                viewModel.createLabelRule(
                                    label = labelName.trim().ifEmpty { category },
                                    useSender = useSender,
                                    senderContains = senderContains,
                                    useBody = useBody,
                                    bodyContains = bodyContains,
                                    useMerchant = useMerchant,
                                    merchantContains = merchantContains,
                                    applyToThisTransaction = applyToThis,
                                )
                            },
                            enabled = matchesPreview && labelName.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.label_rule_save))
                        }
                        OutlinedButton(
                            onClick = { showLabelForm = false; ruleNeedsCriteria = false },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }

            Text(stringResource(R.string.label_type), style = MaterialTheme.typography.titleMedium)
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
                onValueChange = { value ->
                    notes = value
                    selectedTags = HashtagParser.merge(value, selectedTags).toSet()
                },
                label = { Text(stringResource(R.string.label_notes)) },
                supportingText = { Text(stringResource(R.string.label_notes_hashtag_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
            )

            Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipTags = (HashtagParser.SUGGESTED + selectedTags).distinctBy { it.lowercase() }
                chipTags.forEach { tag ->
                    val selected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTags = if (selected) {
                                selectedTags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                            } else {
                                selectedTags + tag
                            }
                        },
                        label = { Text("#$tag") },
                    )
                }
            }

            SurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.split_toggle_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.split_toggle_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = splitOn, onCheckedChange = { splitOn = it })
                }
                if (splitOn) {
                    Spacer(Modifier.height(12.dp))
                    splitRows.forEachIndexed { index, (name, amount) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { value ->
                                    splitRows = splitRows.toMutableList().also {
                                        it[index] = value to amount
                                    }
                                },
                                label = { Text(stringResource(R.string.split_person_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { value ->
                                    splitRows = splitRows.toMutableList().also {
                                        it[index] = name to value.filter { c -> c.isDigit() || c == '.' }
                                    }
                                },
                                label = { Text(stringResource(R.string.split_amount_owed)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                prefix = { Text("₹") },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    TextButton(
                        onClick = { splitRows = splitRows + ("" to "") },
                    ) { Text(stringResource(R.string.split_add_person)) }
                }
            }

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
                        val parsedAmount = runCatching {
                            Money.ofRupees(amountText.trim())
                        }.getOrNull()
                        val parsedDate = runCatching {
                            LocalDate.parse(dateText.trim())
                        }.getOrNull()
                        if (parsedAmount == null || parsedAmount.amount.signum() <= 0) return@Button
                        if (parsedDate == null) return@Button
                        val zone = ZoneId.systemDefault()
                        val oldLocal = current.timestamp.atZone(zone)
                        val newTimestamp = parsedDate
                            .atTime(oldLocal.toLocalTime())
                            .atZone(zone)
                            .toInstant()
                        val shares = if (splitOn) {
                            splitRows.mapNotNull { (name, amt) ->
                                val n = name.trim()
                                val money = runCatching { Money.ofRupees(amt.trim()) }.getOrNull()
                                if (n.isEmpty() || money == null || money.amount.signum() <= 0) {
                                    null
                                } else {
                                    SplitShare(n, money)
                                }
                            }
                        } else {
                            emptyList()
                        }
                        viewModel.save(
                            current.copy(
                                amount = parsedAmount,
                                merchant = merchantText.trim().takeIf { it.isNotEmpty() },
                                timestamp = newTimestamp,
                                category = category.trim().ifEmpty { current.category },
                                notes = notes.trim().ifEmpty { null },
                                type = type,
                                tags = HashtagParser.merge(notes, selectedTags),
                                isSplit = splitOn && shares.isNotEmpty(),
                                splitShares = shares,
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
                    onClick = { confirmDelete = true },
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

@Composable
private fun friendlyPaymentMode(mode: PaymentMode): String = stringResource(
    when (mode) {
        PaymentMode.UPI -> R.string.payment_mode_upi
        PaymentMode.CARD_CREDIT -> R.string.payment_mode_card_credit
        PaymentMode.CARD_DEBIT -> R.string.payment_mode_card_debit
        PaymentMode.NET_BANKING -> R.string.payment_mode_net_banking
        PaymentMode.WALLET -> R.string.payment_mode_wallet
        PaymentMode.CASH -> R.string.payment_mode_cash
        PaymentMode.FASTAG -> R.string.payment_mode_fastag
        PaymentMode.UNKNOWN -> R.string.payment_mode_unknown
    },
)

@Composable
private fun CriterionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
        )
    }
}
