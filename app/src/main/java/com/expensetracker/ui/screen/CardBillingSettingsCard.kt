package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.repository.CardStatementRepository
import com.expensetracker.domain.model.CardStatement
import com.expensetracker.sms.CardDueReminderScheduler
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.maskableFormatInr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CardBillingViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val statements: CardStatementRepository,
    private val reminderScheduler: CardDueReminderScheduler,
) : ViewModel() {

    val startDay: StateFlow<Int> = settingsDao.observe(AppSettings.CC_BILLING_CYCLE_START_DAY)
        .map { it?.toIntOrNull()?.coerceIn(1, 28) ?: 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val upcoming: StateFlow<List<CardStatement>> = statements.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        viewModelScope.launch {
            settingsDao.put(SettingsEntity(AppSettings.CC_BILLING_CYCLE_START_DAY, clamped.toString()))
        }
    }

    fun deleteStatement(id: Long) {
        viewModelScope.launch {
            reminderScheduler.cancel(id)
            statements.delete(id)
        }
    }
}

@Composable
fun CardBillingSettingsCard(
    viewModel: CardBillingViewModel = hiltViewModel(),
) {
    val startDay by viewModel.startDay.collectAsState()
    val statements by viewModel.upcoming.collectAsState()
    var sliderDay by remember(startDay) { mutableIntStateOf(startDay) }
    val dueFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    SurfaceCard {
        Text(stringResource(R.string.settings_billing_cycle_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_billing_cycle_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_billing_cycle_day, sliderDay),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = sliderDay.toFloat(),
            onValueChange = { sliderDay = it.toInt().coerceIn(1, 28) },
            onValueChangeFinished = { viewModel.setStartDay(sliderDay) },
            valueRange = 1f..28f,
            steps = 26,
        )
        OutlinedTextField(
            value = sliderDay.toString(),
            onValueChange = { raw ->
                val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: return@OutlinedTextField
                sliderDay = parsed.coerceIn(1, 28)
                viewModel.setStartDay(sliderDay)
            },
            label = { Text(stringResource(R.string.settings_billing_cycle_day_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
    }

    if (statements.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SurfaceCard {
            Text(stringResource(R.string.settings_card_dues_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_card_dues_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            statements.forEach { stmt ->
                val total = stmt.totalDue?.maskableFormatInr() ?: "—"
                val min = stmt.minDue?.maskableFormatInr()
                val card = stmt.cardLast4?.let { "••••$it" } ?: "—"
                val bank = stmt.bank ?: stringResource(R.string.card_due_unknown_bank)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "$bank $card",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            R.string.settings_card_dues_row,
                            stmt.dueDate.format(dueFmt),
                            total,
                            min ?: "—",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.deleteStatement(stmt.id) },
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) { Text(stringResource(R.string.action_delete)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
