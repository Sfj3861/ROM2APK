package com.sfj3861.rom2apk.core.porting

/**
 * Source templates copied into every generated game-port project.
 * Touch and keyboard controls share one GameAction contract.
 */
internal object AndroidInputTemplates {
    fun gamePortSource() = """
        package generated.port

        import android.app.Activity
        import android.view.View

        enum class GameAction {
            UP, DOWN, LEFT, RIGHT,
            A, B, X, Y,
            START, SELECT, L, R
        }

        interface GeneratedGamePort {
            val title: String
            fun createSession(activity: Activity): GeneratedGameSession
        }

        interface GeneratedGameSession {
            /** Native game rendering surface created by the port backend. */
            val view: View
            fun onCreate()
            fun onPause()
            fun onResume()
            fun onDestroy()

            /** Receives the combined state from touch controls and physical keys. */
            fun onInputChanged(pressed: Set<GameAction>)
        }
    """.trimIndent()

    fun controlsViewSource() = """
        package generated.port

        import android.content.Context
        import android.graphics.Canvas
        import android.graphics.Paint
        import android.graphics.RectF
        import android.view.MotionEvent
        import android.view.View
        import kotlin.math.abs
        import kotlin.math.hypot
        import kotlin.math.min

        /**
         * Transparent, multi-touch virtual gamepad. It only reports game actions;
         * the generated native game code owns all rendering and game logic.
         */
        class VirtualControlsView(
            context: Context,
            private val onActionsChanged: (Set<GameAction>) -> Unit
        ) : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val pointerActions = mutableMapOf<Int, Set<GameAction>>()
            private val buttonRects = mutableMapOf<GameAction, RectF>()
            private var dpadCenterX = 0f
            private var dpadCenterY = 0f
            private var dpadRadius = 0f
            private var buttonRadius = 0f
            private var selectRect = RectF()
            private var startRect = RectF()
            private var lRect = RectF()
            private var rRect = RectF()
            private val capturedPointers = mutableSetOf<Int>()

            init {
                isClickable = true
                contentDescription = "Controles táctiles: cruceta, A, B, X, Y, L, R, Select y Start"
            }

            private val activeActions: Set<GameAction>
                get() = pointerActions.values.flatten().toSet()

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val unit = min(w, h).toFloat()
                dpadCenterX = w * 0.17f
                dpadCenterY = h * 0.76f
                dpadRadius = unit * 0.115f
                buttonRadius = unit * 0.052f
                buttonRects.clear()
                buttonRects[GameAction.A] = circleRect(w * 0.89f, h * 0.75f, buttonRadius)
                buttonRects[GameAction.B] = circleRect(w * 0.82f, h * 0.83f, buttonRadius)
                buttonRects[GameAction.X] = circleRect(w * 0.82f, h * 0.67f, buttonRadius)
                buttonRects[GameAction.Y] = circleRect(w * 0.75f, h * 0.75f, buttonRadius)
                val smallW = unit * 0.085f
                val smallH = unit * 0.045f
                selectRect = RectF(w * 0.43f, h * 0.90f, w * 0.43f + smallW, h * 0.90f + smallH)
                startRect = RectF(w * 0.55f, h * 0.90f, w * 0.55f + smallW, h * 0.90f + smallH)
                val shoulderRadius = unit * 0.045f
                lRect = circleRect(w * 0.13f, h * 0.20f, shoulderRadius)
                rRect = circleRect(w * 0.87f, h * 0.20f, shoulderRadius)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                drawDpad(canvas)
                drawButton(canvas, GameAction.A, "A")
                drawButton(canvas, GameAction.B, "B")
                drawButton(canvas, GameAction.X, "X")
                drawButton(canvas, GameAction.Y, "Y")
                drawPill(canvas, GameAction.SELECT, selectRect, "SEL")
                drawPill(canvas, GameAction.START, startRect, "START")
                drawPill(canvas, GameAction.L, lRect, "L")
                drawPill(canvas, GameAction.R, rRect, "R")
            }

            private fun drawDpad(canvas: Canvas) {
                paint.color = 0x77505050
                paint.style = Paint.Style.FILL
                canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius, paint)
                paint.color = if (GameAction.UP in activeActions) 0xDDEAEAEA.toInt() else 0x99FFFFFF.toInt()
                canvas.drawText("▲", dpadCenterX, dpadCenterY - dpadRadius * 0.40f, centeredText())
                paint.color = if (GameAction.DOWN in activeActions) 0xDDEAEAEA.toInt() else 0x99FFFFFF.toInt()
                canvas.drawText("▼", dpadCenterX, dpadCenterY + dpadRadius * 0.72f, centeredText())
                paint.color = if (GameAction.LEFT in activeActions) 0xDDEAEAEA.toInt() else 0x99FFFFFF.toInt()
                canvas.drawText("◀", dpadCenterX - dpadRadius * 0.43f, dpadCenterY + dpadRadius * 0.16f, centeredText())
                paint.color = if (GameAction.RIGHT in activeActions) 0xDDEAEAEA.toInt() else 0x99FFFFFF.toInt()
                canvas.drawText("▶", dpadCenterX + dpadRadius * 0.43f, dpadCenterY + dpadRadius * 0.16f, centeredText())
            }

            private fun drawButton(canvas: Canvas, action: GameAction, label: String) {
                val rect = buttonRects[action] ?: return
                paint.style = Paint.Style.FILL
                paint.color = if (action in activeActions) 0xDDA6C8FF.toInt() else 0x88505050.toInt()
                canvas.drawCircle(rect.centerX(), rect.centerY(), buttonRadius, paint)
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawText(label, rect.centerX(), rect.centerY() + buttonRadius * 0.32f, centeredText())
            }

            private fun drawPill(canvas: Canvas, action: GameAction, rect: RectF, label: String) {
                paint.style = Paint.Style.FILL
                paint.color = if (action in activeActions) 0xDDA6C8FF.toInt() else 0x99505050.toInt()
                canvas.drawRoundRect(rect, rect.height(), rect.height(), paint)
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawText(label, rect.centerX(), rect.centerY() + rect.height() * 0.30f, centeredText(rect.height() * 0.46f))
            }

            private fun centeredText(size: Float = buttonRadius * 0.65f): Paint {
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = size
                paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                return paint
            }

            private fun circleRect(x: Float, y: Float, radius: Float) =
                RectF(x - radius, y - radius, x + radius, y + radius)

            private fun actionsAt(x: Float, y: Float): Set<GameAction> {
                if (selectRect.contains(x, y)) return setOf(GameAction.SELECT)
                if (startRect.contains(x, y)) return setOf(GameAction.START)
                if (lRect.contains(x, y)) return setOf(GameAction.L)
                if (rRect.contains(x, y)) return setOf(GameAction.R)
                buttonRects.forEach { (action, rect) ->
                    if (hypot((x - rect.centerX()).toDouble(), (y - rect.centerY()).toDouble()) <= buttonRadius) {
                        return setOf(action)
                    }
                }

                val dx = x - dpadCenterX
                val dy = y - dpadCenterY
                if (hypot(dx.toDouble(), dy.toDouble()) > dpadRadius) return emptySet()
                val actions = mutableSetOf<GameAction>()
                if (abs(dx) > dpadRadius * 0.22f) {
                    actions.add(if (dx < 0) GameAction.LEFT else GameAction.RIGHT)
                }
                if (abs(dy) > dpadRadius * 0.22f) {
                    actions.add(if (dy < 0) GameAction.UP else GameAction.DOWN)
                }
                return actions
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val index = event.actionIndex
                        val id = event.getPointerId(index)
                        val actions = actionsAt(event.getX(index), event.getY(index))
                        if (event.actionMasked == MotionEvent.ACTION_DOWN && actions.isEmpty()) {
                            // Let the game surface receive ordinary touchscreen input.
                            return false
                        }
                        if (actions.isNotEmpty()) {
                            capturedPointers.add(id)
                            pointerActions[id] = actions
                        }
                        publish()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        for (index in 0 until event.pointerCount) {
                            val id = event.getPointerId(index)
                            if (id !in capturedPointers) continue
                            val actions = actionsAt(event.getX(index), event.getY(index))
                            if (actions.isEmpty()) pointerActions.remove(id) else pointerActions[id] = actions
                        }
                        publish()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val id = event.getPointerId(event.actionIndex)
                        capturedPointers.remove(id)
                        pointerActions.remove(id)
                        publish()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        capturedPointers.clear()
                        pointerActions.clear()
                        publish()
                    }
                }
                invalidate()
                return capturedPointers.isNotEmpty() || event.actionMasked == MotionEvent.ACTION_CANCEL
            }

            private fun publish() = onActionsChanged(activeActions)
        }
    """.trimIndent()

    fun activitySource() = """
        package generated.port

        import android.app.Activity
        import android.os.Bundle
        import android.view.KeyEvent
        import android.view.ViewGroup
        import android.widget.FrameLayout

        class GeneratedPortActivity : Activity() {
            private var session: GeneratedGameSession? = null
            private val keyboardKeys = mutableMapOf<Int, GameAction>()
            private var touchActions: Set<GameAction> = emptySet()

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val gamePort: GeneratedGamePort = GeneratedGameImplementation()
                val gameSession = gamePort.createSession(this)
                session = gameSession

                val root = FrameLayout(this)
                root.addView(
                    gameSession.view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                val controls = VirtualControlsView(this) { actions ->
                    touchActions = actions
                    dispatchCombinedInput()
                }
                root.addView(
                    controls,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                setContentView(root)
                gameSession.onCreate()
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                val action = keyToAction(event.keyCode) ?: return super.dispatchKeyEvent(event)
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> keyboardKeys[event.keyCode] = action
                    KeyEvent.ACTION_UP -> keyboardKeys.remove(event.keyCode)
                    else -> return true
                }
                dispatchCombinedInput()
                return true
            }

            private fun keyToAction(keyCode: Int): GameAction? = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> GameAction.UP
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> GameAction.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> GameAction.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> GameAction.RIGHT
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_A -> GameAction.A
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_BUTTON_B -> GameAction.B
                KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_BUTTON_X -> GameAction.X
                KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_BUTTON_Y -> GameAction.Y
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_START -> GameAction.START
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BUTTON_SELECT -> GameAction.SELECT
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_L1 -> GameAction.L
                KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_BUTTON_R1 -> GameAction.R
                else -> null
            }

            private fun dispatchCombinedInput() {
                session?.onInputChanged((keyboardKeys.values + touchActions).toSet())
            }

            override fun onPause() {
                keyboardKeys.clear()
                touchActions = emptySet()
                dispatchCombinedInput()
                session?.onPause()
                super.onPause()
            }

            override fun onResume() {
                super.onResume()
                session?.onResume()
            }

            override fun onDestroy() {
                session?.onDestroy()
                session = null
                super.onDestroy()
            }
        }
    """.trimIndent()
}
