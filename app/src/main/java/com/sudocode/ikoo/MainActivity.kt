package com.sudocode.ikoo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.core.vision.VisionEngineRegistry
import com.sudocode.ikoo.gallery_ai.GemmaLiteRtVisionEngine
import com.sudocode.ikoo.nfc.IKooNfcManager
import com.sudocode.ikoo.ui.IKooApp
import com.sudocode.ikoo.ui.theme.IKooTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nfcManager: IKooNfcManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        VisionEngineRegistry.setActive(GemmaLiteRtVisionEngine())

        lifecycleScope.launch(Dispatchers.IO) {
            AIEngineRegistry.initialize(applicationContext)
        }

        nfcManager = IKooNfcManager(
            activity = this,
            onIKooPayloadReceived = { payload ->
                handleIKooNfcPayload(payload)
            }
        )

        setContent {
            IKooTheme {
                IKooApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val manager = nfcManager ?: return

        when {
            !manager.isNfcSupported() -> {
                // NFC not available. App continues normally.
            }

            !manager.isNfcEnabled() -> {
                Toast.makeText(
                    this,
                    "NFC is off. Turn it on to use iKoo Tap Connect.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                manager.startReading()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcManager?.stopReading()
    }

    private fun handleIKooNfcPayload(payload: String) {
        Toast.makeText(
            this,
            "iKoo NFC action received: $payload",
            Toast.LENGTH_LONG
        ).show()

        // Later connect this to:
        // 1. Event join screen
        // 2. CalendarActionManager
        // 3. VoiceOverlayActivity
        // 4. Assistant memory
    }
}
