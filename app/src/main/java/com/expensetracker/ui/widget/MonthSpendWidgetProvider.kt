package com.expensetracker.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.expensetracker.R
import com.expensetracker.ui.MainActivity
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Home-screen glance of this month's spend. [cacheAmount] writes the latest
 * total and immediately pushes it to every placed widget instance.
 */
class MonthSpendWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        const val PREFS = "month_spend_widget"
        const val KEY_AMOUNT = "amount_text"

        fun cacheAmount(
            context: Context,
            amount: BigDecimal,
            amountsHidden: Boolean = false,
        ) {
            val formatted = if (amountsHidden) {
                context.getString(R.string.amount_hidden_mask)
            } else {
                NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AMOUNT, formatted)
                .apply()
            pushToWidgets(context)
        }

        fun pushToWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MonthSpendWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_month_spend)
            views.setTextViewText(
                R.id.widget_title,
                context.getString(R.string.widget_month_spend),
            )
            val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AMOUNT, null)
            views.setTextViewText(
                R.id.widget_amount,
                cached ?: context.getString(R.string.widget_amount_placeholder),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
