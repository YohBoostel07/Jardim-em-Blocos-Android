package com.yohboostel.jardim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GardenGameView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val board = Array(9) { BooleanArray(9) }
    private val colors = Array(9) { IntArray(9) }
    private val palette = intArrayOf(
        Color.rgb(83, 190, 139), Color.rgb(93, 164, 226),
        Color.rgb(246, 183, 91), Color.rgb(204, 137, 211),
        Color.rgb(239, 119, 126), Color.rgb(103, 199, 198)
    )

    // Peças de 1 a 5 blocos, incluindo formatos clássicos e algumas variações.
    private val shapes = listOf(
        listOf(0 to 0),
        listOf(0 to 0, 1 to 0),
        listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 1),
        listOf(1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
        listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2),
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2)
    )

    private data class Piece(val cells: List<Pair<Int, Int>>, val color: Int)
    private data class Spark(val x: Float, val y: Float, val color: Int, val size: Float)

    private var pieces = List(3) { newPiece() }
    private var selected = 0
    private var score = 0
    private var best = context.getSharedPreferences("garden_game", Context.MODE_PRIVATE)
        .getInt("best_score", 0)
    private var level = 1
    private var gameOver = false
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var boardSize = 0f
    private var slotsTop = 0f
    private var dragging = false
    private var pointerX = 0f
    private var pointerY = 0f
    private var previewRow = -1
    private var previewCol = -1
    private var previewValid = false
    private var gardenStage = 0
    private var comboLabel = ""
    private var comboAlpha = 0f
    private var sparks = emptyList<Spark>()
    private var effectProgress = 0f

    init {
        isFocusable = true
        isClickable = true
    }

    private fun newPiece(): Piece {
        // Peças pequenas aparecem com frequência suficiente para manter a partida fluida.
        val index = when (Random.nextInt(100)) {
            in 0..17 -> Random.nextInt(0, 6)
            in 18..54 -> Random.nextInt(1, 10)
            else -> Random.nextInt(9, shapes.size)
        }
        return Piece(shapes[index], palette[Random.nextInt(palette.size)])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(17, 55, 48), Color.rgb(29, 91, 67), Color.rgb(38, 105, 73)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        drawGardenBackdrop(canvas, w, h)
        drawHeader(canvas, w, h)

        boardSize = min(w * .90f, h * .465f)
        cell = boardSize / 9f
        boardLeft = (w - boardSize) / 2f
        boardTop = h * .225f
        drawBoard(canvas)

        slotsTop = boardTop + boardSize + h * .075f
        drawPieceTray(canvas, w, h)
        drawFooter(canvas, w, h)

        if (sparks.isNotEmpty()) drawSparks(canvas)
        if (gameOver) drawGameOver(canvas, w, h)
    }

    private fun drawGardenBackdrop(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(24, 255, 255, 255)
        for (i in 0..6) {
            val x = w * (i / 6f)
            c.drawCircle(x, h * .19f, w * .11f, paint)
        }
        paint.color = Color.rgb(249, 218, 139)
        c.drawCircle(w * .86f, h * .115f, w * .043f, paint)
        paint.color = Color.argb(45, 255, 255, 255)
        c.drawOval(w * .06f, h * .12f, w * .28f, h * .17f, paint)
        c.drawOval(w * .20f, h * .105f, w * .42f, h * .16f, paint)
    }

    private fun drawHeader(c: Canvas, w: Float, h: Float) {
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.WHITE
        paint.textSize = w * .073f
        c.drawText("Jardim em Blocos", w / 2f, h * .073f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(203, 235, 211)
        paint.textSize = w * .027f
        c.drawText("CULTIVE  •  COMBINE  •  FLORESÇA", w / 2f, h * .105f, paint)

        drawStat(c, w * .27f, h * .165f, "PONTOS", score.toString())
        drawStat(c, w * .73f, h * .165f, "RECORDE", max(best, score).toString())
        paint.color = Color.rgb(255, 226, 160)
        paint.textSize = w * .027f
        paint.textAlign = Paint.Align.CENTER
        c.drawText("NÍVEL $level", w / 2f, h * .177f, paint)
    }

    private fun drawStat(c: Canvas, x: Float, y: Float, label: String, value: String) {
        val box = RectF(x - width * .19f, y - height * .026f, x + width * .19f, y + height * .047f)
        rounded(c, box, height * .018f, Color.argb(105, 5, 35, 29))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(193, 224, 200)
        paint.textSize = width * .025f
        c.drawText(label, x, y, paint)
        paint.color = Color.WHITE
        paint.textSize = width * .043f
        c.drawText(value, x, y + height * .039f, paint)
    }

    private fun drawBoard(c: Canvas) {
        val outer = RectF(boardLeft - 8f, boardTop - 8f, boardLeft + boardSize + 8f, boardTop + boardSize + 8f)
        rounded(c, outer, 24f, Color.argb(220, 8, 39, 33))
        for (r in 0..8) for (col in 0..8) {
            val left = boardLeft + col * cell
            val top = boardTop + r * cell
            val rect = RectF(left + 2f, top + 2f, left + cell - 2f, top + cell - 2f)
            val occupied = board[r][col]
            rounded(c, rect, cell * .17f, if (occupied) colors[r][col] else Color.argb(76, 207, 239, 215))
            if (occupied) {
                paint.color = Color.argb(65, 255, 255, 255)
                c.drawCircle(left + cell * .28f, top + cell * .24f, cell * .085f, paint)
                paint.color = Color.argb(35, 0, 0, 0)
                c.drawRoundRect(rect.left + cell * .08f, rect.bottom - cell * .13f, rect.right - cell * .08f, rect.bottom - cell * .04f, cell * .05f, cell * .05f, paint)
            }
        }
        if (dragging && previewRow >= 0 && previewCol >= 0) {
            val p = pieces[selected]
            p.cells.forEach { (dx, dy) ->
                val row = previewRow + dy
                val col = previewCol + dx
                if (row in 0..8 && col in 0..8) {
                    val rect = RectF(
                        boardLeft + col * cell + 2f, boardTop + row * cell + 2f,
                        boardLeft + (col + 1) * cell - 2f, boardTop + (row + 1) * cell - 2f
                    )
                    rounded(c, rect, cell * .17f, if (previewValid) withAlpha(p.color, 155) else Color.argb(135, 235, 85, 85))
                }
            }
        }
    }

    private fun drawPieceTray(c: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(231, 246, 230)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .031f
        c.drawText("ESCOLHA UMA PEÇA", w / 2f, slotsTop - h * .022f, paint)

        val slotWidth = w * .275f
        for (i in 0..2) {
            val centerX = w * (.18f + i * .32f)
            val centerY = slotsTop + h * .055f
            val active = i == selected
            val rect = RectF(centerX - slotWidth / 2f, centerY - h * .047f, centerX + slotWidth / 2f, centerY + h * .047f)
            rounded(c, rect, 19f, if (active) Color.rgb(247, 231, 184) else Color.argb(105, 7, 38, 32))
            if (active) {
                paint.color = Color.rgb(128, 94, 49)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                c.drawRoundRect(rect, 19f, 19f, paint)
                paint.style = Paint.Style.FILL
            }
            if (!(dragging && i == selected)) drawPiece(c, pieces[i], centerX, centerY, min(cell * .39f, h * .026f))
            paint.color = if (active) Color.rgb(108, 80, 41) else Color.argb(190, 235, 247, 232)
            paint.textSize = w * .022f
            c.drawText(if (active) "SELECIONADA" else "TOQUE", centerX, rect.bottom - h * .008f, paint)
        }
        if (comboAlpha > 0f) {
            paint.color = withAlpha(Color.rgb(255, 226, 139), (comboAlpha * 255).toInt())
            paint.textSize = w * .044f
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            c.drawText(comboLabel, w / 2f, slotsTop + h * .12f, paint)
        }
    }

    private fun drawFooter(c: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(218, 241, 220)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .026f
        c.drawText("Complete linhas e colunas para abrir espaço", w / 2f, h * .955f, paint)
    }

    private fun drawPiece(c: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val maxX = piece.cells.maxOf { it.first } + 1
        val maxY = piece.cells.maxOf { it.second } + 1
        val unit = min(size * 2.35f / maxX, size * 2.35f / maxY)
        piece.cells.forEach { (x, y) ->
            val left = cx - maxX * unit / 2f + x * unit
            val top = cy - maxY * unit / 2f + y * unit
            val rect = RectF(left, top, left + unit - 3f, top + unit - 3f)
            rounded(c, rect, 7f, piece.color)
            paint.color = Color.argb(65, 255, 255, 255)
            c.drawCircle(rect.left + rect.width() * .28f, rect.top + rect.height() * .24f, unit * .09f, paint)
        }
    }

    private fun drawSparks(c: Canvas) {
        sparks.forEach {
            paint.color = withAlpha(it.color, (255 * (1f - effectProgress)).toInt().coerceIn(0, 255))
            c.drawCircle(it.x, it.y - effectProgress * height * .07f, it.size * (1f - effectProgress * .35f), paint)
        }
    }

    private fun drawGameOver(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(205, 4, 23, 19)
        c.drawRect(0f, 0f, w, h, paint)
        val panel = RectF(w * .10f, h * .37f, w * .90f, h * .59f)
        rounded(c, panel, 28f, Color.rgb(239, 231, 197))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .068f
        c.drawText("Fim de jogo", w / 2f, h * .445f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .034f
        c.drawText("Pontuação: $score  •  Recorde: $best", w / 2f, h * .495f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .032f
        c.drawText("TOQUE PARA JOGAR NOVAMENTE", w / 2f, h * .55f, paint)
    }

    private fun rounded(c: Canvas, rect: RectF, radius: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(rect, radius, radius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun fits(piece: Piece, row: Int, col: Int): Boolean = piece.cells.all { (dx, dy) ->
        row + dy in 0..8 && col + dx in 0..8 && !board[row + dy][col + dx]
    }

    private fun hasAnyMove(piece: Piece): Boolean =
        (0..8).any { row -> (0..8).any { col -> fits(piece, row, col) } }

    private fun placePiece(piece: Piece, row: Int, col: Int) {
        piece.cells.forEach { (dx, dy) ->
            board[row + dy][col + dx] = true
            colors[row + dy][col + dx] = piece.color
        }
        score += piece.cells.size * 5

        // Identifica todas as linhas antes de limpar, evitando contar cruzamentos duas vezes.
        val fullRows = (0..8).filter { r -> (0..8).all { board[r][it] } }
        val fullCols = (0..8).filter { colIndex -> (0..8).all { r -> board[r][colIndex] } }
        val clearedCells = linkedSetOf<Pair<Int, Int>>()
        fullRows.forEach { r -> (0..8).forEach { colIndex -> clearedCells.add(r to colIndex) } }
        fullCols.forEach { colIndex -> (0..8).forEach { r -> clearedCells.add(r to colIndex) } }

        val lines = fullRows.size + fullCols.size
        if (lines > 0) {
            clearedCells.forEach { (r, colIndex) ->
                sparks = sparks + Spark(
                    boardLeft + (colIndex + .5f) * cell,
                    boardTop + (r + .5f) * cell,
                    colors[r][colIndex],
                    cell * .17f
                )
                board[r][colIndex] = false
                colors[r][colIndex] = Color.TRANSPARENT
            }
            val linePoints = lines * 100 + if (lines > 1) (lines - 1) * 75 else 0
            score += linePoints
            gardenStage = (gardenStage + lines).coerceAtMost(8)
            comboLabel = when {
                lines >= 4 -> "INCRÍVEL! $lines LINHAS!"
                lines == 3 -> "MEGA COMBO!"
                lines == 2 -> "ÓTIMO COMBO!"
                else -> "+100"
            }
            comboAlpha = 1f
            animateEffects()
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        } else {
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        if (score > best) {
            best = score
            context.getSharedPreferences("garden_game", Context.MODE_PRIVATE)
                .edit().putInt("best_score", best).apply()
        }
        level = 1 + score / 500
        pieces = pieces.toMutableList().also { it[selected] = newPiece() }
        gameOver = pieces.none { hasAnyMove(it) }
    }

    private fun animateEffects() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 430
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                effectProgress = it.animatedValue as Float
                comboAlpha = 1f - effectProgress
                postInvalidateOnAnimation()
            }
            start()
        }
        sparks = sparks.takeLast(70)
    }

    private fun resetGame() {
        for (r in 0..8) for (col in 0..8) {
            board[r][col] = false
            colors[r][col] = Color.TRANSPARENT
        }
        score = 0
        level = 1
        gardenStage = 0
        gameOver = false
        pieces = List(3) { newPiece() }
        selected = 0
        sparks = emptyList()
        comboAlpha = 0f
        dragging = false
        previewRow = -1
        previewCol = -1
    }

    private fun updatePreview(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        if (x in boardLeft..(boardLeft + boardSize) && y in boardTop..(boardTop + boardSize)) {
            // A célula tocada será o canto superior esquerdo da peça.
            previewCol = ((x - boardLeft) / cell).toInt()
            previewRow = ((y - boardTop) / cell).toInt()
            previewValid = fits(pieces[selected], previewRow, previewCol)
        } else {
            previewRow = -1
            previewCol = -1
            previewValid = false
        }
    }

    private fun selectPieceAt(x: Float) {
        val index = ((x / width) * 3f).toInt().coerceIn(0, 2)
        selected = index
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver) {
            if (event.action == MotionEvent.ACTION_UP) {
                resetGame()
                invalidate()
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y > slotsTop - height * .04f && event.y < slotsTop + height * .105f) {
                    selectPieceAt(event.x)
                    dragging = true
                    updatePreview(event.x, event.y)
                    invalidate()
                    return true
                }
                if (event.x in boardLeft..(boardLeft + boardSize) && event.y in boardTop..(boardTop + boardSize)) {
                    dragging = true
                    updatePreview(event.x, event.y)
                    invalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updatePreview(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    updatePreview(event.x, event.y)
                    if (previewValid && previewRow >= 0 && previewCol >= 0) {
                        placePiece(pieces[selected], previewRow, previewCol)
                    }
                    dragging = false
                    previewRow = -1
                    previewCol = -1
                    invalidate()
                } else if (event.y > slotsTop - height * .04f && event.y < slotsTop + height * .105f) {
                    selectPieceAt(event.x)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                previewRow = -1
                previewCol = -1
                invalidate()
                return true
            }
        }
        return true
    }
}
