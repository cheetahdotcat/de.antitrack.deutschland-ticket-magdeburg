package com.succ.antitrack.magdeburg

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val OFFICIAL = listOf(
    "X-TAF-DEVICE-ID" to
        "Die Settings.Secure.ANDROID_ID des Geräts — eine dauerhafte Geräte-ID, die eine Neuinstallation " +
        "überlebt — bei JEDEM API-Aufruf, auf Ticket-Endpunkten zusätzlich als deviceId-Parameter. " +
        "Daraus entsteht pro Gerät ein Nutzungs- und Buchungsprofil.",
    "X-TAF-USER-MARKETING-SETTINGS: YES" to
        "Wird gesendet, sobald Marketing zugestimmt wurde — markiert das Gerät für Marketing und " +
        "Adjust-Umsatzattribution.",
    "Adjust" to
        "Werbe-Attribution und Umsatz-Tracking pro Ereignis (Ticketkauf, Rad-/Auto-/E-Scooter-Buchungen, " +
        "Check-ins), an das Gerät gebunden.",
    "Firebase Analytics / Crashlytics" to
        "Nutzungsanalyse über die Werbe-ID (GAID), Sitzungen, Absturz- und ANR-Berichte mit Gerätemodell, " +
        "OS und Build.",
    "FCM" to "Push-Registrierungstoken, ans Backend geschickt und an das Konto gebunden.",
    "Mapbox" to "Kartenansicht samt Gesten- und Interaktions-Telemetrie bei jeder Kartennutzung.",
    "Play App Indexing + Privacy Sandbox Ads" to "Inhalts-Indexierung und Signale zur Werbe-Attribution.",
    "Stetho (Facebook)" to
        "Ein Netzwerk-Inspektor für die Chrome-DevTools — im Release-Build enthalten (in v1.0.5 inaktiv). " +
        "Ein Entwicklerwerkzeug, das nie ausgeliefert werden sollte.",
)

private val OURS = listOf(
    "Authorization" to "das Bearer-Token",
    "User-Agent" to "${MvbApi.USER_AGENT} (so erwartet es die API)",
    "Accept-Language" to "de-DE",
    "X-TAF-DEVICE-ID" to "zufällige UUID, pro Anfrage neu",
)

@Composable
fun ManifestoScreen(firstRun: Boolean, onDone: () -> Unit) {
    TopBar("Warum diese App existiert", onBack = if (firstRun) null else onDone)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Rotkaeppchen()
        Text(
            "Die offizielle MVB-App funkt ständig nach Hause. Diese App ist das Gegenteil: Sie macht genau die " +
                "API-Aufrufe, die nötig sind, um dein Deutschlandticket anzuzeigen — und sonst nichts.",
        )

        Section("Anti-Tracking")
        Text(
            "Keine Analytics, keine Attribution, kein APM. Kein Adjust, kein Mixpanel, keine " +
                "„Feature-Usage“-Pings. Der einzige Netzwerkverkehr geht an das Ticket-Backend " +
                "(prod.tafmobile.de), für das Ticket. Keine Hintergrunddienste, keine Alarme, keine WorkManager, " +
                "keine Broadcast-Receiver. Einzige Berechtigung: INTERNET.",
        )

        Section("Anti-Firebase")
        Text(
            "Kein Firebase, gar keins: kein Analytics, kein Crashlytics, kein FCM-Push, keine Installations, " +
                "kein AppCheck, kein Remote Config, kein Firestore. Die offizielle App bringt all das mit — dazu " +
                "Mapbox-Telemetrie, Google Play App Indexing und Privacy-Sandbox-Werbeattribution.",
        )

        Section("Was die offizielle App sendet")
        Table(OFFICIAL)

        Section("Was diese App sendet")
        Table(OURS)

        Spacer(Modifier.height(8.dp))
        if (firstRun) Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Verstanden") }
    }
}

private val WOLF = listOf(
    "Aber, liebe MVB-App, warum hast du so große Tracking-SDKs?" to "Damit wir dich besser verfolgen können.",
    "Und warum kennst du meine Geräte-ID?" to "Damit wir dich überall wiedererkennen.",
    "Und warum redest du so viel mit Adjust und Firebase?" to "Damit wir dich besser vermarkten können.",
)

@Composable
private fun Rotkaeppchen() {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WOLF.forEach { (q, a) ->
            Text("„$q“", fontStyle = FontStyle.Italic)
            Text("„$a“", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
        Text("… und dann fraß sie deine Daten.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Table(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        rows.forEachIndexed { i, (field, meaning) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    field,
                    modifier = Modifier.weight(0.38f).padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(meaning, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
