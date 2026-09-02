package com.expensetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.expensetracker.R
import com.expensetracker.domain.model.Money

/** Whether rupee amounts in the current subtree should be masked. Defaults to visible. */
val LocalAmountsHidden = compositionLocalOf { false }

/** [Money.formatInr], masked with dots when [LocalAmountsHidden] is true. */
@Composable
fun Money.maskableFormatInr(): String =
    if (LocalAmountsHidden.current) stringResource(R.string.amount_hidden_mask) else formatInr()

@Composable
fun AmountVisibilityToggle(
    amountsHidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (amountsHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(
                if (amountsHidden) {
                    R.string.dashboard_show_amounts_a11y
                } else {
                    R.string.dashboard_hide_amounts_a11y
                },
            ),
        )
    }
}
