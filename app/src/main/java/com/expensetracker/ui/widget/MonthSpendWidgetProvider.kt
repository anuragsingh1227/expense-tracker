package com.expensetracker.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.expensetracker.R
import com.expensetracker.ui.MainActivity
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Simple home-screen glance: month spend label. The total is filled by
 * [MonthSpendWidgetUpdater] when the app has a chance to refresh; until then
 * it shows a placeholder so the widget always renders.
 */
class MonthSpendWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_month_spend)
            views.setTextViewText(
                R.id.widget_title,
                context.getString(R.string.widget_month_spend),
            )
            val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AMOUNT, null)
            views.setTextViewText(
                R.id.widget_amount,
                cached ?: context.getString(R.string.amount_hidden_mask),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        const val PREFS = "month_spend_widget"
        const val KEY_AMOUNT = "amount_text"

        fun cacheAmount(context: Context, amount: BigDecimal) {
            val formatted = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AMOUNT, formatted)
                .apply()
        }
    }
}
