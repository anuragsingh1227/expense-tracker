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
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.LabelRuleCatalog
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
    private val labelRuleCatalog: LabelRuleCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow<Transaction?>(null)
    val state: StateFlow<Transaction?> = _state.asStateFlow()

    private val _ruleSavedMessage = MutableStateFlow<String?>(null)
    val ruleSavedMessage: StateFlow<String?> = _ruleSavedMessage.asStateFlow()

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

        var category by remember(current.id, current.category) { mutableStateOf(current.category) }
        var type by remember(current.id, current.type) { mutableStateOf(current.type) }
        var notes by remember(current.id) { mutableStateOf(current.notes.orEmpty()) }
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
                    viewModel.clearRuleMessage()
                }
                "NEED_CRITERIA" -> viewModel.clearRuleMessage()
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
                MetaRow(stringResource(R.string.label_sender), current.sender ?: "—")
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
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
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
                            onClick = { showLabelForm = false },
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
                                type = type,
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
