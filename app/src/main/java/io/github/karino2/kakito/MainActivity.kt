package io.github.karino2.kakito

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.createBitmap
import android.graphics.Typeface
import android.net.Uri
import io.github.karino2.supernote.EinkPenCanvas

class MainActivity : AppCompatActivity() {
    val lastUri : Uri?
        get() = Entry.lastUri(this)

    fun gotoSetup()
    {
        Intent(this, SetupActivity::class.java)
            .also { startActivity(it) }
    }


    private var entryList = mutableListOf<Entry>()

    private fun reloadEntries() {
        entryList.clear()
        entryList.addAll(Entry.fromLastUri(this).reversed())
        if (currentIndex >= entryList.size) {
            currentIndex = if (entryList.isEmpty()) 0 else entryList.size - 1
        }
        updateUI()
    }

    var currentIndex = 0
    var isKanjiVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setLogo(R.mipmap.ic_launcher)
            setDisplayUseLogoEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.buttonClear).setOnClickListener {
            clearWriting()
        }

        val previewFont = Typeface.createFromAsset(assets, "fonts/YujiSyuku-Regular.ttf")
        findViewById<TextView>(R.id.textViewYomi).typeface = previewFont
        findViewById<TextView>(R.id.textViewKanji).typeface = previewFont

        lastUri ?: return gotoSetup()

        reloadEntries()
        penCanvas.requestRawDrawing()
    }

    private fun clearWriting() {
        clearBackendBmp()
        clearPenCanvas()
    }

    private fun clearBackendBmp() {
        if(penCanvas.isBackendInitialized) penCanvas.clear()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_kanji -> {
                isKanjiVisible = !isKanjiVisible
                updateUI()
                true
            }
            R.id.action_prev -> {
                if (currentIndex > 0) {
                    currentIndex--
                    clearBackendBmp()
                    updateUI()
                }
                true
            }
            R.id.action_next -> {
                if (currentIndex < entryList.size - 1) {
                    currentIndex++
                    clearBackendBmp()
                    updateUI()
                }
                true
            }
            R.id.action_add_entry -> {
                Intent(this, EntryActivity::class.java).apply {
                    putExtra("REQUEST_ADD_ENTRY", true)
                }.also { startActivity(it) }
                true
            }
            R.id.action_edit_entries -> {
                Intent(this, EntryActivity::class.java)
                    .also { startActivity(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI() {
        if (entryList.isEmpty()) {
            findViewById<TextView>(R.id.textViewYomi).text = ""
            findViewById<TextView>(R.id.textViewKanji).text = ""
            return
        }
        withTempNoRawRendering {
            val entry = entryList[currentIndex]
            findViewById<TextView>(R.id.textViewYomi).text = entry.yomi
            findViewById<TextView>(R.id.textViewKanji).apply {
                text = entry.kanji
                visibility = if (isKanjiVisible) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    private val penCanvas by lazy { findViewById<EinkPenCanvas>(R.id.penCanvas) }

    private fun clearPenCanvas(bmp: Bitmap? = null): Boolean {
        if(!penCanvas.isBackendInitialized)
            return false

        if(bmp != null) {
            penCanvas.loadBitmap(bmp)
        } else {
            penCanvas.clear()
        }
        return true
    }

    private fun withTempNoRawRendering(f: ()->Unit) = penCanvas.withTempNoRawRendering(f)


    override fun onResume() {
        super.onResume()
        reloadEntries()
        penCanvas.requestRawDrawing()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) penCanvas.requestRawDrawing()
    }

    override fun onPause() {
        penCanvas.ensureCloseRawRendering()
        super.onPause()
    }

    override fun onDestroy() {
        penCanvas.ensureCloseRawRendering()
        super.onDestroy()
    }

}
