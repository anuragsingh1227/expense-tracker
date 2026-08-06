package com.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.R
import com.expensetracker.ui.screen.DashboardScreen
import com.expensetracker.ui.screen.SettingsScreen
import com.expensetracker.ui.screen.TransactionDetailScreen
import com.expensetracker.ui.screen.TransactionsScreen
import com.expensetracker.ui.theme.ExpenseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTheme {
                AppRoot()
            }
        }
    }
}

private sealed class Tab(val route: String, val label: Int, val icon: ImageVector) {
    data object Dashboard : Tab("dashboard", R.string.tab_dashboard, Icons.Filled.Home)
    data object Transactions : Tab("transactions", R.string.tab_transactions, Icons.AutoMirrored.Filled.List)
    data object Settings : Tab("settings", R.string.tab_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Dashboard, Tab.Transactions, Tab.Settings)

@Composable
private fun AppRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(RequiredPermissions.allGranted(ctx)) }
    var skippedPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = RequiredPermissions.allGranted(ctx)
        if (permissionsGranted) {
            appViewModel.runInboxScan(forceFullLookback = true)
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            appViewModel.runInboxScan(forceFullLookback = true)
        }
    }

    if (!permissionsGranted && !skippedPermission) {
        PermissionOnboarding(
            onGrant = { launcher.launch(RequiredPermissions.names()) },
            onSkip = { skippedPermission = true },
        )
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = stringResource(tab.label),
                                )
                            },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Dashboard.route) {
                DashboardScreen(
                    onOpenTransaction = { nav.navigate("transaction/$it") },
                    onOpenSettings = { nav.navigate(Tab.Settings.route) },
                )
            }
            composable(Tab.Transactions.route) {
                TransactionsScreen(onOpenTransaction = { nav.navigate("transaction/$it") })
            }
            composable(Tab.Settings.route) {
                val scanning by appViewModel.scanning.collectAsState()
                val lastScan by appViewModel.lastScan.collectAsState()
                val backupState by appViewModel.backupState.collectAsState()
                val cleanupRemoved by appViewModel.cleanupRemoved.collectAsState()
                SettingsScreen(
                    onRescanInbox = { appViewModel.runInboxScan(forceFullLookback = true) },
                    scanning = scanning,
                    lastScan = lastScan,
                    backupState = backupState,
                    onExportBackup = { appViewModel.exportBackup(it) },
                    onImportBackup = { appViewModel.importBackup(it) },
                    onPurgeSpam = { appViewModel.purgeSpam() },
                    cleanupRemoved = cleanupRemoved,
                    onPermissionsChanged = {
                        permissionsGranted = true
                        appViewModel.runInboxScan(forceFullLookback = true)
                    },
                )
            }
            composable("transaction/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                TransactionDetailScreen(transactionId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun PermissionOnboarding(
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    val benefits = listOf(
        Icons.Outlined.Sms to stringResource(R.string.onboarding_benefit_sms),
        Icons.Outlined.Lock to stringResource(R.string.onboarding_benefit_privacy),
        Icons.Outlined.AccountBalanceWallet to stringResource(R.string.onboarding_benefit_auto),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            benefits.forEach { (icon, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Column {
            Button(
                onClick = onGrant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.permission_skip))
            }
            Text(
                stringResource(R.string.onboarding_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
