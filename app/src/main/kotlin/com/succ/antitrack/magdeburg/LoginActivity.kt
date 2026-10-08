package com.succ.antitrack.magdeburg

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokens = intent?.data?.let(::fromUri)
        if (tokens != null) {
            Store(this).saveTokens(tokens.access, tokens.refresh)
        } else {
            Toast.makeText(this, "Der Link enthält keinen refreshToken.", Toast.LENGTH_LONG).show()
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_JUST_LOGGED_IN, tokens != null)
        )
        finish()
    }
}
