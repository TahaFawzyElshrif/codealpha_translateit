package tmp.android.translateit

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.Locale

class translate : AppCompatActivity() {
    val subscription_key = ""
    val endpoint = "https://api.cognitive.microsofttranslator.com/"
    val region = "italynorth"

    lateinit var translate_B:Button
    lateinit var copy:Button
    lateinit var paste:Button
    lateinit var clear:Button
    lateinit var to_:Spinner
    lateinit var from_:Spinner
    lateinit var text:EditText
    lateinit var output:TextView
    lateinit var play_sound :ImageButton

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_translate)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        intializObjects()
        set_spinners()
        reset()
        set_translate()
        setTTS()
        setCopy()
        setPaste()
        setClear()

    }

    fun setCopy(){
        copy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager //Cast
            val clip = ClipData.newPlainText("label", output.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

    }
    fun setPaste(){
        paste.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val pasteData = item?.text.toString()
                text.setText(pasteData)
            }
        }
    }
    fun reset(){
        text.setText("")
        output.text = ""
        to_.setSelection(0)
        from_.setSelection(1)


    }
    private fun setClear(){
        clear.setOnClickListener {
            reset()
        }

    }



    fun setTTS() {
        val localeMap = mapOf(
            "en" to Locale.ENGLISH,
            "ar" to Locale("ar"),
            "fr" to Locale.FRENCH,
            "de" to Locale.GERMAN,
            "es" to Locale("es"),
            "zh-Hans" to Locale.SIMPLIFIED_CHINESE,
            "ja" to Locale.JAPANESE
        )

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
            }
        }

        play_sound.setOnClickListener {
            val textToSpeak = output.text.toString()
            val selectedLangCode = to_.selectedItem.toString()

            val selectedLocale = localeMap[selectedLangCode] ?: Locale.ENGLISH
            val result = tts.setLanguage(selectedLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported on this device", Toast.LENGTH_SHORT).show()
            } else {
                if (textToSpeak.isNotBlank()) {
                    tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    fun intializObjects() {
        translate_B = findViewById<Button>(R.id.translate)
        copy = findViewById<Button>(R.id.copy)
        paste = findViewById<Button>(R.id.paste)
        clear = findViewById<Button>(R.id.clear)

        to_ = findViewById<Spinner>(R.id.to)
        from_ = findViewById<Spinner>(R.id.from)
        text = findViewById<EditText>(R.id.main_text)
        output = findViewById<TextView>(R.id.answer)
        play_sound = findViewById<ImageButton>(R.id.play_button)
    }
    fun set_spinners(){
        val languageCodes = listOf("en", "ar", "fr", "de", "es", "zh-Hans", "ja") // Azure supported codes
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageCodes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        from_.adapter = adapter
        to_.adapter = adapter

    }
    fun check_input() :Boolean{
        return to_.selectedItem.toString().isNotEmpty() || from_.selectedItem.toString().isNotEmpty() || from_ != to_ || text.text.isNotEmpty()
    }
    fun set_translate(){
        translate_B.setOnClickListener {
            if (check_input()){
                lifecycleScope.launch {
                val translated_text : String = translateText(text.text.toString(),from_.selectedItem.toString(),to_.selectedItem.toString())
                output.text = translated_text

                }

            }else{
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Please Check your input !")
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

            }


        }
    }
    suspend fun translateText(
        inputText: String,
        fromLang: String,
        toLang: String
    ): String {


        val url = "$endpoint/translate?api-version=3.0&from=$fromLang&to=$toLang"

        // Body: JSON array with single object containing the input text
        val jsonBody = """[{"Text":"$inputText"}]"""

        val client = OkHttpClient()

        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Ocp-Apim-Subscription-Key", subscription_key)
            .addHeader("Ocp-Apim-Subscription-Region", region)
            .addHeader("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) { //like java thread with IO include network related
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext "Error: ${response.code}" // return at the withContext not on function
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response"
                val jsonArray = org.json.JSONArray(responseBody)
                val translations = jsonArray.getJSONObject(0).getJSONArray("translations")
                val translatedText = translations.getJSONObject(0).getString("text")
                return@withContext translatedText
            } catch (e: Exception) {
                return@withContext "Exception: ${e.message}"
            }
        }
    }


}