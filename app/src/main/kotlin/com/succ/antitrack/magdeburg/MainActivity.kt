package com.succ.antitrack.magdeburg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Manifesto : Screen
    data object Login : Screen
    data object OidcLogin : Screen
    data object Subscriptions : Screen
    data class TicketScreen(val sub: Subscription) : Screen
    data object Settings : Screen
    data object LinkSubscription : Screen
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_JUST_LOGGED_IN = "justLoggedIn"
    }

    private lateinit var store: Store
    private lateinit var api: MvbApi

    private var loginEvents by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        api = MvbApi(store)

        if (savedInstanceState == null) wipeWebSession()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(store, api, loginEvents)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_JUST_LOGGED_IN, false)) loginEvents++
    }
}

private fun home(store: Store): Screen = when {
    !store.manifestoSeen -> Screen.Manifesto
    store.loggedIn && store.autoOpenTicket && store.lastTicket != null -> store.lastTicket!!.let { (id, name) ->
        Screen.TicketScreen(Subscription(id, name, null, null, null))
    }
    store.loggedIn -> Screen.Subscriptions
    else -> Screen.Login
}

@Composable
fun App(store: Store, api: MvbApi, loginEvents: Int) {
    var screen by remember { mutableStateOf(home(store)) }
    LaunchedEffect(loginEvents) {
        if (loginEvents > 0) {
            store.manifestoSeen = true
            screen = Screen.Subscriptions
        }
    }
    val back: () -> Unit = { screen = if (store.loggedIn) Screen.Subscriptions else Screen.Login }
    BackHandler(enabled = screen != Screen.Subscriptions && screen != Screen.Login) { back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = screen) {
            Screen.Manifesto -> ManifestoScreen(firstRun = !store.manifestoSeen) {
                store.manifestoSeen = true
                back()
            }
            Screen.Login -> LoginScreen(
                onLoggedIn = { screen = Screen.Subscriptions },
                onOidc = { screen = Screen.OidcLogin },
                onManifesto = { screen = Screen.Manifesto },
                store = store,
            )
            Screen.OidcLogin -> OidcLoginScreen(
                store = store,
                onLoggedIn = { screen = Screen.Subscriptions },
                onBack = { screen = Screen.Login },
            )
            Screen.LinkSubscription -> LinkSubscriptionScreen(
                api = api,
                onLinked = { screen = Screen.Subscriptions },
                onBack = { screen = Screen.Subscriptions },
                onLoggedOut = { screen = Screen.Login },
            )
            Screen.Subscriptions -> SubscriptionsScreen(
                api = api,
                onLink = { screen = Screen.LinkSubscription },
                onOpen = { screen = Screen.TicketScreen(it) },
                onManifesto = { screen = Screen.Manifesto },
                onSettings = { screen = Screen.Settings },
                onLoggedOut = { screen = Screen.Login },
            )
            is Screen.TicketScreen -> TicketScreen(store, api, s.sub, onBack = back, onLoggedOut = { screen = Screen.Login })
            Screen.Settings -> SettingsScreen(store, onBack = back, onLoggedOut = { screen = Screen.Login })
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ Zurück") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        actions()
    }
}

@Composable
fun LoginScreen(store: Store, onLoggedIn: () -> Unit, onOidc: () -> Unit, onManifesto: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    TopBar("Anmelden") { TextButton(onClick = onManifesto) { Text("Warum?") } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onOidc, modifier = Modifier.fillMaxWidth()) { Text("Mit MVB-Konto anmelden") }
        Spacer(Modifier.height(16.dp))
        Text("Alternativ", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            label = { Text("Deep-Link oder Refresh-Token") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val t = parseLogin(text)
            if (t == null) {
                error = "Darin ist kein refreshToken zu finden."
            } else {
                store.saveTokens(t.access, t.refresh)
                onLoggedIn()
            }
        }) { Text("Token übernehmen") }
    }
}

@Composable
fun SubscriptionsScreen(
    api: MvbApi,
    onLink: () -> Unit,
    onOpen: (Subscription) -> Unit,
    onManifesto: () -> Unit,
    onSettings: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var subs by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var unlinking by remember { mutableStateOf<Subscription?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        loading = true; error = null
        try {
            subs = api.subscriptions()
        } catch (e: ApiException) {
            if (e.loggedOut) onLoggedOut() else error = e.message
        } finally {
            loading = false
        }
    }
    TopBar("Meine Tickets") {
        TextButton(onClick = onManifesto) { Text("Warum?") }
        TextButton(onClick = onSettings) { Text("Einstellungen") }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { reload++ }) { Text("Erneut versuchen") }
            }
            subs.isEmpty() -> {
                Text("Keine aktiven Abonnements auf diesem Konto.")
                Button(onClick = onLink) { Text("Bestehendes Abo hinzufügen") }
                OutlinedButton(onClick = { reload++ }) { Text("Aktualisieren") }
            }
            else -> {
                subs.forEach { s ->
                    Card(onClick = { onOpen(s) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            s.code?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            s.validFrom?.let { Text("ab ${it.take(10)}", style = MaterialTheme.typography.bodySmall) }
                            s.validity?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            if (s.code != null) {
                                TextButton(onClick = { unlinking = s }) {
                                    Text("Vom Konto entfernen", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reload++ }) { Text("Aktualisieren") }
                    OutlinedButton(onClick = onLink) { Text("Abo hinzufügen") }
                }
            }
        }
    }
    unlinking?.let { s ->
        var understood by remember(s) { mutableStateOf(false) }
        val danger = MaterialTheme.colorScheme.error
        AlertDialog(
            onDismissRequest = { unlinking = null },
            icon = { Text("⚠", fontSize = 32.sp, color = danger) },
            title = { Text("Abo vom Konto entfernen?", color = danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.name} (${s.code})", fontWeight = FontWeight.Bold)
                    Text(
                        "Das Ticket verschwindet sofort aus dieser und der offiziellen App. Bei einer Kontrolle " +
                            "kannst du es dann nicht mehr vorzeigen.",
                    )
                    Text(
                        "Das Abo wird nicht gekündigt. Zurück kommt es nur über „Abo hinzufügen“ mit " +
                            "Vertragsnummer, Name und Geburtsdatum.",
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { understood = !understood },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = understood, onCheckedChange = { understood = it })
                        Text("Ich habe meine Vertragsnummer zur Hand.")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = understood,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = danger,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        unlinking = null
                        scope.launch {
                            try {
                                if (!api.unlinkSubscription(s.code!!)) error = "Entfernen fehlgeschlagen."
                                reload++
                            } catch (e: ApiException) {
                                if (e.loggedOut) onLoggedOut() else error = e.message
                            }
                        }
                    },
                ) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun TicketScreen(store: Store, api: MvbApi, sub: Subscription, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    LaunchedEffect(sub.orderId) { store.lastTicket = sub.orderId to sub.name }
    ShowOverLockscreen(store.showOnLockscreen)
    var loading by remember { mutableStateOf(true) }
    var ticket by remember { mutableStateOf<Ticket?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pull by remember { mutableIntStateOf(0) }
    LaunchedEffect(pull) {
        loading = true; error = null
        try {
            ticket = api.ticket(sub.orderId)
        } catch (e: ApiException) {
            if (e.loggedOut) onLoggedOut() else error = e.message
        } finally {
            loading = false
        }
    }
    val dark = isSystemInDarkTheme()
    val sheet = if (dark) Color(0xFF323232) else Color(0xFFF6F6F6)
    val ink = if (dark) Color.White else Color.Black
    Column(Modifier.fillMaxSize().background(sheet)) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.25f)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { pull++ }, enabled = !loading) {
                Text("Neu abrufen", color = ink.copy(alpha = if (loading) 0.4f else 0.8f))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(ink.copy(alpha = 0.08f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = ink, fontSize = 18.sp) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            val t = ticket
            when {
                t == null -> {}
                t.html != null -> TicketHtml(t.html, Modifier.fillMaxSize().background(Color.White))
                t.pdf != null -> TicketPdf(t.pdf, Modifier.fillMaxSize().background(Color.White))
                else -> Text("Das Ticket enthält weder HTML noch PDF.", color = ink, modifier = Modifier.padding(16.dp))
            }
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            error?.let {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { pull++ }) { Text("Erneut versuchen") }
                }
            }
        }

        val t = ticket
        if (t?.html != null && t.pdf != null) {
            var showPdf by remember(t) { mutableStateOf(false) }
            TextButton(onClick = { showPdf = !showPdf }) { Text(if (showPdf) "PDF ausblenden" else "PDF zeigen") }
            if (showPdf) TicketPdf(t.pdf, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    var pinned by remember { mutableStateOf(store.pinnedDeviceId) }
    var autoOpen by remember { mutableStateOf(store.autoOpenTicket) }
    var lockscreen by remember { mutableStateOf(store.showOnLockscreen) }
    var askLockscreen by remember { mutableStateOf(false) }
    TopBar("Einstellungen", onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Feste Geräte-ID", style = MaterialTheme.typography.titleMedium)
                Text(
                    pinned ?: "Aus — neue zufällige ID pro Anfrage",
                    style = MaterialTheme.typography.bodySmall.let {
                        if (pinned != null) it.copy(fontFamily = FontFamily.Monospace) else it
                    },
                )
            }
            Switch(
                checked = pinned != null,
                onCheckedChange = { on ->
                    store.pinnedDeviceId = if (on) java.util.UUID.randomUUID().toString() else null
                    pinned = store.pinnedDeviceId
                },
            )
        }
        SettingSwitch("Ticket beim Start öffnen", "Öffnet das zuletzt angesehene Ticket direkt.", autoOpen) {
            store.autoOpenTicket = it; autoOpen = it
        }
        SettingSwitch("Über dem Sperrbildschirm zeigen", "Das Ticket bleibt sichtbar, wenn das Telefon sperrt.", lockscreen) {
            if (it) askLockscreen = true else { store.showOnLockscreen = false; lockscreen = false }
        }
        if (askLockscreen) {
            AlertDialog(
                onDismissRequest = { askLockscreen = false },
                title = { Text("Ticket über dem Sperrbildschirm?") },
                text = {
                    Text(
                        "Wer dein gesperrtes Telefon in die Hand bekommt, sieht dann das offene Ticket mit Name und " +
                            "QR-Code, ohne zu entsperren. Nur das Ticket — der Rest der App bleibt gesperrt, und " +
                            "neu abrufen geht erst nach dem Entsperren.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        store.showOnLockscreen = true; lockscreen = true; askLockscreen = false
                    }) { Text("Erlauben") }
                },
                dismissButton = { TextButton(onClick = { askLockscreen = false }) { Text("Abbrechen") } },
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { store.logout(); onLoggedOut() }) { Text("Abmelden") }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ShowOverLockscreen(enabled: Boolean) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity ?: return
    androidx.compose.runtime.DisposableEffect(enabled) {
        fun apply(on: Boolean) {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(on)
            } else {
                @Suppress("DEPRECATION")
                val flag = android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
            }
        }
        apply(enabled)
        onDispose { apply(false) }
    }
}
