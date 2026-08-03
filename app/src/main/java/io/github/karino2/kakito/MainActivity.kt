package io.github.karino2.kakito

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri

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

        val zenKurenaido = Typeface.createFromAsset(assets, "fonts/ZenKurenaido-Regular.ttf")
        findViewById<TextView>(R.id.textViewYomi).typeface = zenKurenaido
        findViewById<TextView>(R.id.textViewKanji).typeface = zenKurenaido

        lastUri ?: return gotoSetup()

        reloadEntries()
        touchHelper
    }

    private fun clearWriting() {
        clearBackendBmp()
        withTempNoRawRendering {
            clearSurfaceView()
        }
    }

    private fun clearBackendBmp() {
        bmpCanvas?.drawColor(Color.WHITE)
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

    /*
    SurfaceView関連。面倒なのでActivityに書いてしまう。
     */
    private val surface: SurfaceView by lazy { findViewById(R.id.surfaceView)}

    private val penWidth = 5f
    private val penPaint = Paint().apply {
        isAntiAlias = true
        // isDither = true
        color = Color.BLACK
        style = Paint.Style.STROKE
        // strokeJoin = Paint.Join.ROUND
        // strokeCap = Paint.Cap.ROUND
        strokeWidth = penWidth
    }

    private val bmpPaint = Paint(Paint.DITHER_FLAG)
    private var _bitmap: Bitmap? = null
    private val bitmap: Bitmap?
        get() {
            if (_bitmap == null && surface.width > 0 && surface.height > 0) {
                _bitmap = createBitmap(surface.width, surface.height)
            }
            return _bitmap
        }

    private var _bmpCanvas: Canvas? = null
    private val bmpCanvas: Canvas?
        get() {
            val b = bitmap ?: return null
            if (_bmpCanvas == null) {
                _bmpCanvas = Canvas(b).apply { drawColor(Color.WHITE) }
            }
            return _bmpCanvas
        }

    fun drawScribbleToBitmap(
        points: List<TouchPoint>
    ) {
        if (points.isEmpty()) return
        val canvas = bmpCanvas ?: return
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            // skip strange jump point.
            if (abs(prePoint.y - point.y) >= 30)
                continue
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        canvas.drawPath(path, penPaint)
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            applyRawDrawingSettings()
        }
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {
            applyRawDrawingSettings()
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            ensureCloseRawRendering()
        }
    }

    private val touchHelper by lazy {
        val helper = TouchHelper.create(surface, object: RawInputCallback(){
            override fun onBeginRawDrawing(
                p0: Boolean,
                p1: TouchPoint?
            ) {
            }

            override fun onEndRawDrawing(
                p0: Boolean,
                p1: TouchPoint?
            ) {
            }

            override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
            }

            override fun onRawDrawingTouchPointListReceived(plist: TouchPointList?) {
                drawScribbleToBitmap(plist!!.points)
            }

            override fun onBeginRawErasing(
                p0: Boolean,
                p1: TouchPoint?
            ) {
            }

            override fun onEndRawErasing(
                p0: Boolean,
                p1: TouchPoint?
            ) {
            }

            override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
            }

            override fun onRawErasingTouchPointListReceived(p0: TouchPointList?) {
            }
        })
        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyRawDrawingSettings() }
        surface.holder.addCallback(surfaceCallback)
        helper
    }

    private fun clearSurfaceView(bmp: Bitmap? = null): Boolean {
        val holder = surface.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        bmp?.let {
            canvas.drawBitmap(it, 0f, 0f, bmpPaint)
        }
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    private fun surfaceVisibleRect(): Rect {
        val limit = Rect()
        surface.getLocalVisibleRect(limit)

        return limit
    }

    private fun ensureCloseRawRendering() {
        if (touchHelper.isRawDrawingRenderEnabled) {
            touchHelper.setRawDrawingEnabled(false)
            touchHelper.isRawDrawingRenderEnabled = false
            touchHelper.closeRawDrawing()
        }
    }

    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        touchHelper.isRawDrawingRenderEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    private fun withTempNoRawRendering(f: ()->Unit) {
        val isRawRendering = touchHelper.isRawDrawingRenderEnabled
        if (isRawRendering)
        {
            touchHelper.setRawDrawingEnabled(false)
        }
        f()
        if (isRawRendering)
        {
            clearSurfaceView(bitmap)
            touchHelper.setRawDrawingEnabled(true)
        }
    }

    private var lastOpenedLimit: Rect? = null

    private fun applyRawDrawingSettings() {
        val limit = surfaceVisibleRect()
        if (!surface.holder.surface.isValid || limit.width() <= 0 || limit.height() <= 0) return

        // サイズが変わっていたら、バッファ不整合を防ぐため開き直す（黒画面対策の核心）
        if (isRawRenderingBecomesStale(limit)) {
            ensureCloseRawRendering()
            _bitmap = null
            _bmpCanvas = null
        }

        if (!touchHelper.isRawDrawingRenderEnabled) {
            clearSurfaceView(bitmap)
            touchHelper.setStrokeWidth(penWidth)
                .setStrokeColor(Color.BLACK)
                .setLimitRect(limit, emptyList<Rect>())
                .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                .openRawDrawing()
            touchHelper.setRawDrawingEnabled(true)
            touchHelper.isRawDrawingRenderEnabled = true
            lastOpenedLimit = Rect(limit)
        } else {
            touchHelper.setLimitRect(limit, emptyList<Rect>())
        }
    }

    override fun onResume() {
        super.onResume()
        reloadEntries()
        applyRawDrawingSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyRawDrawingSettings()
    }

    override fun onPause() {
        ensureCloseRawRendering()
        super.onPause()
    }

    override fun onDestroy() {
        ensureCloseRawRendering()
        super.onDestroy()
    }

}
