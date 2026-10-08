package com.succ.antitrack.magdeburg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DOB = DateTimeFormatter.ofPattern("d.M.uuuu")

private fun parseDob(s: String): LocalDate? = try {
    LocalDate.parse(s.trim(), DOB).takeIf { it.isBefore(LocalDate.now()) && it.year > 1900 }
} catch (e: DateTimeParseException) {
    null
}

@Composable
fun LinkSubscriptionScreen(api: MvbApi, onLinked: () -> Unit, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    var contract by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val date = parseDob(dob)
    val ready = contract.isNotBlank() && first.isNotBlank() && last.isNotBlank() && date != null

    TopBar("Bestehendes Abo hinzufügen", onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            contract, { contract = it; error = null }, label = { Text("Vertr.-Nr.") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            first, { first = it; error = null }, label = { Text("Vorname") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            last, { last = it; error = null }, label = { Text("Nachname") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            dob, { dob = it; error = null }, label = { Text("Geburtsdatum (TT.MM.JJJJ)") }, singleLine = true,
            isError = dob.isNotBlank() && date == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            if (api.linkSubscription(contract.trim(), first.trim(), last.trim(), date!!)) onLinked()
                            else error = "Kein Abo mit diesen Daten gefunden."
                        } catch (e: ApiException) {
                            if (e.loggedOut) onLoggedOut() else error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Abo suchen") }
        }
    }
}
