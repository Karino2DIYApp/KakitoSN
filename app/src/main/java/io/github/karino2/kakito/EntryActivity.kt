package io.github.karino2.kakito

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class EntryActivity : AppCompatActivity() {

    private val entries = mutableListOf<Entry>()
    private lateinit var adapter: ArrayAdapter<Entry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_entry)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setLogo(R.mipmap.ic_launcher)
            setDisplayUseLogoEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        entries.addAll(Entry.fromLastUri(this))

        val previewFont = Typeface.createFromAsset(assets, "fonts/YujiSyuku-Regular.ttf")

        adapter = object : ArrayAdapter<Entry>(this, R.layout.list_item_entry, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_entry, parent, false)
                val entry = getItem(position)!!
                view.findViewById<TextView>(R.id.textViewYomi).apply {
                    typeface = previewFont
                    text = entry.yomi
                }
                view.findViewById<TextView>(R.id.textViewKanji).apply {
                    typeface = previewFont
                    text = entry.kanji
                }
                return view
            }
        }

        val listView = findViewById<ListView>(R.id.listViewEntries)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            showEditDialog(entries[position], position)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            showDeleteConfirmDialog(position)
            true
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showEditDialog(null, -1)
        }

        if (intent.getBooleanExtra("REQUEST_ADD_ENTRY", false)) {
            showEditDialog(null, -1)
        }
    }

    private fun showEditDialog(entry: Entry?, position: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_entry, null)
        val editYomi = view.findViewById<TextInputEditText>(R.id.editTextYomi)
        val editKanji = view.findViewById<TextInputEditText>(R.id.editTextKanji)

        entry?.let {
            editYomi.setText(it.yomi)
            editKanji.setText(it.kanji)
        }

        AlertDialog.Builder(this)
            .setTitle(if (entry == null) "エントリー追加" else "エントリー編集")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val newEntry = Entry(editYomi.text.toString(), editKanji.text.toString())
                if (position == -1) {
                    entries.add(newEntry)
                } else {
                    entries[position] = newEntry
                }
                saveAndRefresh()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteConfirmDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("削除確認")
            .setMessage("このエントリーを削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                entries.removeAt(position)
                saveAndRefresh()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun saveAndRefresh() {
        Entry.writeToLastUri(this, entries)
        adapter.notifyDataSetChanged()
    }
}