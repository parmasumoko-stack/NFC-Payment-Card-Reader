package com.example.nfcpaymentcardreader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.devnied.emvnfccard.parser.EmvTemplate
import java.text.SimpleDateFormat
import java.util.Locale

// Provider to bridge communication between library and hardware
class NfcProvider(private val isoDep: IsoDep) : com.github.devnied.emvnfccard.parser.IProvider {
    override fun transceive(pCommand: ByteArray): ByteArray = isoDep.transceive(pCommand)
    override fun getAt(): ByteArray = isoDep.historicalBytes ?: isoDep.hiLayerResponse ?: byteArrayOf()
}

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var cardNumberText: TextView
    private lateinit var expiryText: TextView
    private lateinit var nameText: TextView
    private lateinit var typeText: TextView
    private lateinit var statusText: TextView
    private var lastReadNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- UI CONSTRUCTION (NO XML) ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(60, 100, 60, 60)
        }

        val header = TextView(this).apply {
            text = "NFC Payment Card Scanner"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A1C1E"))
            setPadding(0, 0, 0, 60)
        }
        rootLayout.addView(header)

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(60, 60, 60, 60)
            elevation = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createLabel(str: String) = TextView(this).apply {
            text = str
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, 15, 0, 5)
        }

        cardNumberText = TextView(this).apply {
            text = "•••• •••• •••• ••••"
            textSize = 20f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.BLACK)
        }

        expiryText = TextView(this).apply {
            text = "MM/YY"
            textSize = 18f
            setTextColor(Color.BLACK)
        }

        nameText = TextView(this).apply {
            text = "CARDHOLDER NAME"
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        typeText = TextView(this).apply {
            text = "CARD TYPE"
            textSize = 14f
            setTextColor(Color.BLACK)
        }

        cardView.addView(createLabel("CARD NUMBER"))
        cardView.addView(cardNumberText)
        cardView.addView(createLabel("EXPIRY DATE"))
        cardView.addView(expiryText)
        cardView.addView(createLabel("CARDHOLDER NAME"))
        cardView.addView(nameText)
        cardView.addView(createLabel("CARD TYPE"))
        cardView.addView(typeText)

        rootLayout.addView(cardView)

        // Copy Button
        val copyButton = Button(this).apply {
            text = "Copy Card Number"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 50, 0, 0) }

            setOnClickListener {
                if (lastReadNumber.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Card Number", lastReadNumber)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Nothing to copy yet", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(copyButton)

        statusText = TextView(this).apply {
            text = "Hold card to the back of the phone"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4A90E2"))
            setPadding(0, 60, 0, 0)
        }
        rootLayout.addView(statusText)
        setContentView(rootLayout)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        val isoDep = IsoDep.get(tag)
        try {
            isoDep.connect()
            val provider = NfcProvider(isoDep)
            val config = EmvTemplate.Config().apply {
                setContactLess(true)
                setReadAllAids(true)
            }

            val parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build()

            val card = parser.readEmvCard()

            runOnUiThread {
                lastReadNumber = card.cardNumber ?: ""
                cardNumberText.text = lastReadNumber.chunked(4).joinToString(" ")

                // Check if the name is available, otherwise show a friendly message
                val cardholderName = if (!card.holderFirstname.isNullOrEmpty() || !card.holderLastname.isNullOrEmpty()) {
                    "${card.holderFirstname ?: ""} ${card.holderLastname ?: ""}".trim()
                } else {
                    "Name withheld by issuer"
                }

                nameText.text = cardholderName
                typeText.text = card.type.getName() // e.g., VISA, MASTERCARD

                card.expireDate?.let {
                    val sdf = SimpleDateFormat("MM/yy", Locale.UK)
                    expiryText.text = sdf.format(it)
                }

                statusText.text = "✅ Details Loaded"
                statusText.setTextColor(Color.parseColor("#2ECC71"))
            }

        } catch (e: Exception) {
            Log.e("NFC", "Read Error", e)
            runOnUiThread {
                statusText.text = "❌ Read Failed: ${e.message}"
                statusText.setTextColor(Color.RED)
            }
        } finally {
            isoDep.close()
        }
    }
}
