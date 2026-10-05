package com.tripwire.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tripwire.app.service.ProtectionService
import com.tripwire.app.ui.AppViewModel
import com.tripwire.app.ui.CasesScreen
import com.tripwire.app.ui.CheckupScreen
import com.tripwire.app.ui.HomeScreen
import com.tripwire.app.ui.LearnScreen
import com.tripwire.app.ui.Onboarding
import com.tripwire.app.ui.PaidScreen
import com.tripwire.app.ui.SettingsScreen
import com.tripwire.app.ui.TimelineScreen
import com.tripwire.app.ui.TripwireTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val deepLink = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent
        if (graph.settings.current.onboarded) ProtectionService.start(this)

        setContent {
            TripwireTheme {
                val settings by vm.settings.collectAsState()
                val nav = rememberNavController()
                val start = if (settings.onboarded) "home" else "onboarding"

                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        Onboarding(vm) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
                    }
                    composable("home") {
                        HomeScreen(
                            vm,
                            onCases = { nav.navigate("cases") },
                            onCase = { nav.navigate("case/${Uri.encode(it)}") },
                            onPaid = { nav.navigate("paid/") },
                            onSettings = { nav.navigate("settings") },
                            onLearn = { nav.navigate("learn") },
                            onCheckup = { nav.navigate("checkup") },
                        )
                    }
                    composable("cases") { CasesScreen(vm, onCase = { nav.navigate("case/${Uri.encode(it)}") }, onBack = { nav.popBackStack() }) }
                    composable("case/{id}") { e -> TimelineScreen(vm, Uri.decode(e.arguments?.getString("id").orEmpty())) { nav.popBackStack() } }
                    composable("paid/{id}") { e ->
                        PaidScreen(vm, e.arguments?.getString("id")?.takeIf { it.isNotBlank() }?.let(Uri::decode)) { nav.popBackStack() }
                    }
                    composable("paid/") { PaidScreen(vm, null) { nav.popBackStack() } }
                    composable("settings") {
                        SettingsScreen(vm, onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack("home", inclusive = false) })
                    }
                    composable("checkup") { CheckupScreen(vm) { nav.popBackStack() } }
                    composable("learn") { LearnScreen(vm, graph.pack.families) { nav.popBackStack() } }
                }

                // Notices open a timeline; warnings and check-ins open the "I already paid" flow.
                val link by deepLink
                LaunchedEffect(link, settings.onboarded) {
                    val i = link ?: return@LaunchedEffect
                    if (!settings.onboarded) return@LaunchedEffect
                    i.getStringExtra(EXTRA_CASE_ID)?.let { nav.navigate("case/${Uri.encode(it)}") }
                    i.getStringExtra(EXTRA_PAID_CASE)?.let { nav.navigate(if (it.isBlank()) "paid/" else "paid/${Uri.encode(it)}") }
                    deepLink.value = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = intent
    }

    private object Uri {
        fun encode(s: String): String = android.net.Uri.encode(s)
        fun decode(s: String): String = android.net.Uri.decode(s)
    }

    companion object {
        const val EXTRA_CASE_ID = "case_id"
        const val EXTRA_PAID_CASE = "paid_case"
    }
}
