package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.ui.components.SurfaceCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LabelRulesViewModel @Inject constructor(
    private val labelRuleCatalog: LabelRuleCatalog,
    private val repository: TransactionRepository,
) : ViewModel() {

    val rules: StateFlow<List<LabelRuleEntity>> = labelRuleCatalog.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _applyResult = MutableStateFlow<Int?>(null)
    val applyResult: StateFlow<Int?> = _applyResult.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    fun deleteRule(id: Long) {
        viewModelScope.launch { labelRuleCatalog.delete(id) }
    }

    fun applyToPast() {
        if (_applying.value) return
        viewModelScope.launch {
            _applying.value = true
            try {
                _applyResult.value = labelRuleCatalog.applyToPast(repository)
            } finally {
                _applying.value = false
            }
        }
    }

    fun clearApplyResult() {
        _applyResult.value = null
    }
}

@Composable
fun LabelRulesSettingsCard(
    viewModel: LabelRulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsState()
    val applying by viewModel.applying.collectAsState()
    val applyResult by viewModel.applyResult.collectAsState()
    var showEmptyHint by remember { mutableStateOf(false) }

    SurfaceCard {
        Text(stringResource(R.string.settings_label_rules_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_label_rules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (rules.isEmpty()) {
            Text(
                stringResource(R.string.settings_label_rules_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rules.forEach { rule ->
                LabelRuleRow(
                    rule = rule,
                    onDelete = { viewModel.deleteRule(rule.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (rules.isEmpty()) {
                    showEmptyHint = true
                } else {
                    showEmptyHint = false
                    viewModel.applyToPast()
                }
            },
            enabled = !applying && rules.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (applying) {
                    stringResource(R.string.label_rules_applying)
                } else {
                    stringResource(R.string.label_rules_apply_past)
                },
            )
        }
        if (showEmptyHint) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_label_rules_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        applyResult?.let { count ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.label_rules_apply_result, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun LabelRuleRow(
    rule: LabelRuleEntity,
    onDelete: () -> Unit,
) {
    val criteria = buildList {
        rule.senderContains?.let { add(stringResource(R.string.label_rule_sender_prefix, it)) }
        rule.bodyContains?.let { add(stringResource(R.string.label_rule_body_prefix, it)) }
        rule.merchantContains?.let { add(stringResource(R.string.label_rule_merchant_prefix, it)) }
    }.joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rule.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (criteria.isNotBlank()) {
                Text(
                    criteria,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.action_delete))
        }
    }
}
