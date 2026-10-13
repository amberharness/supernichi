package com.example.hobonichiplanner

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    data class Stroke(
        val points: MutableList<Pair<Float, Float>>,
        val eraser: Boolean,
        val width: Float
    )
    private lateinit var paper: PlannerView
    private lateinit var dateLabel: TextView
    private val prefs by lazy { getSharedPreferences("planner_pages", MODE_PRIVATE) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val prettyDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private var pageDate = Calendar.getInstance()

    private fun key(): String = dateFormat.format(pageDate.time)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.WHITE)
        }
        fun button(label: String, action: () -> Unit): Button =
            Button(this).apply { text = label; setOnClickListener { action() } }

        toolbar.addView(button("‹ Day") { savePage(); pageDate.add(Calendar.DAY_OF_MONTH, -1); loadPage() })
        dateLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 4)
        }
        toolbar.addView(dateLabel, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("Day ›") { savePage(); pageDate.add(Calendar.DAY_OF_MONTH, 1); loadPage() })
        root.addView(toolbar)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(4, 0, 4, 4)
        }
        tools.addView(button("Pen") { paper.setEraser(false) })
        tools.addView(button("Eraser") { paper.setEraser(true) })
        tools.addView(button("Clear page") { paper.clearPage(); savePage() })
        root.addView(tools)

        paper = PlannerView()
        root.addView(paper, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadPage()
    }

    private fun loadPage() {
        dateLabel.text = prettyDate.format(pageDate.time)
        paper.setStrokes(readStrokes(key()))
    }

    private fun savePage() {
        val arr = JSONArray()
        paper.exportStrokes().forEach { stroke ->
            val obj = JSONObject()
            obj.put("eraser", stroke.eraser)
            obj.put("width", stroke.width)
            val pts = JSONArray()
            stroke.points.forEach { p ->
                val xy = JSONArray()
                xy.put(p.first); xy.put(p.second)
                pts.put(xy)
            }
            obj.put("points", pts)
            arr.put(obj)
        }
        prefs.edit().putString(key(), arr.toString()).apply()
    }

    private fun readStrokes(date: String): MutableList<Stroke> {
        val result = mutableListOf<Stroke>()
        val raw = prefs.getString(date, null) ?: return result
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val points = mutableListOf<Pair<Float, Float>>()
                val pts = obj.getJSONArray("points")
                for (j in 0 until pts.length()) {
                    val xy = pts.getJSONArray(j)
                    points.add(Pair(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat()))
                }
                result.add(Stroke(points, obj.optBoolean("eraser"), obj.optDouble("width", 2.0).toFloat()))
            }
        } catch (_: Exception) { }
        return result
    }

    override fun onPause() { savePage(); super.onPause() }
    inner class PlannerView : View(this@MainActivity) {
        private val strokes = mutableListOf<Stroke>()
        private var current: Stroke? = null
        private var eraser = false
        private val gridPaint = android.graphics.Paint(3).apply { color = 0xFFE2E2E2.toInt(); strokeWidth = 1f }
        private val marginPaint = android.graphics.Paint(3).apply { color = 0xFFB8B8B8.toInt(); strokeWidth = 1.2f }
        private val inkPaint = android.graphics.Paint(3).apply {
            color = Color.BLACK; strokeWidth = 2.4f; style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        private val textPaint = android.graphics.Paint(3).apply { color = Color.BLACK; textSize = 18f }
        private var cell = 28f

        fun setEraser(on: Boolean) { eraser = on }
        fun setStrokes(value: MutableList<Stroke>) { strokes.clear(); strokes.addAll(value); invalidate() }
        fun exportStrokes(): List<Stroke> = strokes.toList()
        fun clearPage() { strokes.clear(); current = null; invalidate() }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.WHITE)
            cell = max(22f, width / 28f)
            val header = 72f
            var x = 0f
            while (x <= width) { canvas.drawLine(x, header, x, height.toFloat(), gridPaint); x += cell }
            var y = header
            while (y <= height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += cell }
            canvas.drawLine(18f, header, 18f, height.toFloat(), marginPaint)
            canvas.drawText("DAILY NOTES  •  PLANS  •  TO-DO", 22f, 28f, textPaint)
            canvas.drawLine(18f, 45f, width - 18f, 45f, marginPaint)
            for (s in strokes) drawStroke(canvas, s)
            current?.let { drawStroke(canvas, it) }
        }

        private fun drawStroke(canvas: android.graphics.Canvas, stroke: Stroke) {
            if (stroke.points.isEmpty()) return
            val p = android.graphics.Paint(inkPaint)
            p.strokeWidth = stroke.width
            if (stroke.eraser) {
                p.color = Color.WHITE
                p.strokeWidth = cell * 0.85f
            }
            if (stroke.points.size == 1) {
                val q = stroke.points[0]
                canvas.drawPoint(q.first, q.second, p)
                return
            }
            val path = android.graphics.Path()
            path.moveTo(stroke.points[0].first, stroke.points[0].second)
            for (i in 1 until stroke.points.size) path.lineTo(stroke.points[i].first, stroke.points[i].second)
            canvas.drawPath(path, p)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat())
            val y = event.y.coerceIn(48f, height.toFloat())
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Prefer stylus input when present; also allow finger input for testing.
                    current = Stroke(mutableListOf(Pair(x, y)), eraser, if (eraser) cell else 2.4f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    current?.points?.add(Pair(x, y))
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    current?.let { strokes.add(it) }
                    current = null
                    invalidate()
                    savePage()
                    return true
                }
            }
            return true
        }
    }
}
