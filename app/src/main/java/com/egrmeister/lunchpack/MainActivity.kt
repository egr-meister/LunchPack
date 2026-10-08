package com.egrmeister.lunchpack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.egrmeister.lunchpack.ui.LunchPackRoot
import com.egrmeister.lunchpack.ui.OpenPlanRequest
import com.egrmeister.lunchpack.ui.theme.LunchPackTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Set when a reminder notification is tapped: open that date's planned pack. */
    private val openPlanRequests = MutableStateFlow<OpenPlanRequest?>(null)
    private var requestCounter = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as LunchPackApp).container
        setContent {
            LunchPackTheme {
                LunchPackRoot(
                    container = container,
                    openPlanRequests = openPlanRequests,
                    onOpenPlanHandled = { openPlanRequests.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as LunchPackApp).container.onForeground()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_PLAN) return
        val date = intent.getStringExtra(EXTRA_PLAN_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        requestCounter++
        openPlanRequests.value = OpenPlanRequest(date, requestCounter)
    }

    companion object {
        const val ACTION_OPEN_PLAN = "com.egrmeister.lunchpack.action.OPEN_PLAN"
        const val EXTRA_PLAN_DATE = "plan_date"
    }
}
