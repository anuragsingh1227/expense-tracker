package com.expensetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.expensetracker.R
import com.expensetracker.domain.model.Money

/** Whether rupee amounts in the current subtree should be masked. Defaults to visible. */
val LocalAmountsHidden = compositionLocalOf { false }

/** [Money.formatInr], masked with dots when [LocalAmountsHidden] is true. */
@Composable
fun Money.maskableFormatInr(): String =
    if (LocalAmountsHidden.current) stringResource(R.string.amount_hidden_mask) else formatInr()
