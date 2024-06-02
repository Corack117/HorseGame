package com.example.horsegame

import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.horsegame.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.Window
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Coordinate(val x: Int, val y: Int)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var currentCell: Coordinate
    private var board: MutableList<TableRow> = mutableListOf()
    private var boardPoints: Array<Array<Int>> = Array(8) { Array(8) { 0 } }
    private var options: Int = 0
    private var movements: Int = 64
    private var bonus: Int = 8
    private var bonusCell:Boolean = false
    private var bonusPoints: Int = 0
    private var checkMovement = false
    private var isGameOver: Boolean = false
    private var mHandler: Handler? = null
    private var timeInSeconds: Long = 0

    // 0 libre 1 ocupado, bonus 2, Disponibles 9

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initScreenGame()
    }

    @SuppressLint("SetTextI18n")
    fun checkCellClicked(view: View) {
        val coordinate_x = view.tag.toString().substring(1, 2).toInt()
        val coordinate_y = view.tag.toString().substring(2, 3).toInt()
        if (view.tag.toString().contains("bonus")) {
            bonusCell = false
            bonusPoints++
            binding.tvBonusData.text = " + $bonusPoints"
        }

        if (!validatePosition(coordinate_x, coordinate_y)) return
        if (boardPoints[coordinate_x][coordinate_y] == 1) return
        checkOptions(currentCell.x, currentCell.y, true)
        checkCell(coordinate_x, coordinate_y)
        checkOptions(coordinate_x, coordinate_y)
        binding.tvOptionsData.text = "$options"

        growProgressionBonus()
        checkNewBonus()
        checkGameOver()
    }

    fun launchShareGame(view: View) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)

        captureScreenshot(this, binding.mainScreen) { bitmap ->
            bitmap?.let {
                var idGame = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
                idGame = idGame.replace("/", "")
                idGame = idGame.replace(":", "")
                val path = saveImage(bitmap, "$idGame.png")
                val bmpUri = Uri.parse(path)

                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                shareIntent.putExtra(Intent.EXTRA_STREAM, bmpUri)
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Mira mi nuevo record")
                shareIntent.type = "image/jpeg"

                startActivity(Intent.createChooser(shareIntent, "Selecciona en que app deseas compartir"))
            }
        }
    }

    private fun saveImage(bitmap: Bitmap, title: String): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, title)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
            }
            val uri = this.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                this.contentResolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        outputStream.flush()
                        outputStream.close()
                        return uri.toString()
                    }
                }
            }
        }
        return ""
    }

    fun captureScreenshot(activity: Activity, view: View, callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val window: Window = activity.window
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val locationOfView = IntArray(2)
            view.getLocationInWindow(locationOfView)
            try {
                PixelCopy.request(window, android.graphics.Rect(
                    locationOfView[0],
                    locationOfView[1],
                    locationOfView[0] + view.width,
                    locationOfView[1] + view.height
                ), bitmap, { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        callback(bitmap)
                    } else {
                        callback(null)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                callback(null)
            }
        } else {
            callback(null)
        }
    }

    private fun initScreenGame() {
        binding.lyOptions.post {
            startNewGame()
        }
    }

    private fun hideMessage() {
        binding.lyMessage.visibility = View.INVISIBLE
    }

    private fun initBoard(tableLayout: TableLayout) {
        options = 0
        movements = 64
        board.clear()
        boardPoints = Array(8) { Array(8) { 0 } }
        checkMovement = false
        tableLayout.removeAllViews()
        val numRows = 8
        val numCols = 8
        val cellSize = getCellSize(numCols)


        for (row in 0 until numRows) {
            val tableRow = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                weightSum = numCols.toFloat()
            }

            for (col in 0 until numCols) {
                val imageView = createNewCell(row, col, cellSize)
                tableRow.addView(imageView)
            }
            tableLayout.addView(tableRow)
            board.add(tableRow)
        }

        setHorsePosition(tableLayout)
    }

    private fun growProgressionBonus() {
        val layoutParams = binding.bonusBar.layoutParams
        val progress = bonus - (movements % bonus)
        val width = binding.lyOptions.width / bonus

        val newWidthInDp = width * progress
        layoutParams.width = newWidthInDp

        binding.bonusBar.layoutParams = layoutParams
    }

    private fun getCellSize(numberOfCells: Int): Int {
        val screenRealSize = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenRealSize.x = windowMetrics.bounds.width()
            screenRealSize.y = windowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(screenRealSize)
        }
        val sizeWidth = screenRealSize.x
        return sizeWidth / numberOfCells
    }

    private fun createNewCell(row: Int, col: Int, size: Int): ImageView {
        val imageView = ImageView(this).apply {
            id = View.generateViewId()
            tag = "c$row$col"
            layoutParams = TableRow.LayoutParams(size, size, 1f)
            setBackgroundResource(if ((row + col) % 2 == 0)  R.color.white_cell else R.color.black_cell)
            setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { checkCellClicked(it) }
        }

        return imageView
    }

    private fun setHorsePosition(tableLayout: TableLayout) {
        val randomRow = (0..<8).random()
        val randomColumn = (0..<8).random()
        val tableRow = tableLayout.getChildAt(randomRow) as TableRow
        val cell = tableRow.getChildAt(randomColumn) as ImageView
        cell.setBackgroundResource(R.color.selected_cell)
        setHorseColor(cell)
        currentCell = Coordinate(randomRow, randomColumn)
        movements--
        boardPoints[randomRow][randomColumn] = 1
        checkOptions(randomRow, randomColumn)
        binding.tvOptionsData.text = "$options"
        binding.tvMovesNumber.text = "$movements"
    }

    private fun setHorseColor(cell: ImageView) {
        val cellColor: ColorDrawable = cell.background as ColorDrawable
        val whiteCell = ContextCompat.getColor(this, R.color.white_cell)
        cell.setImageResource(R.drawable.horse)
        cell.setColorFilter(
            ContextCompat.getColor(
                this,
                if (cellColor.color == whiteCell)
                    R.color.black
                else
                    R.color.white
            )
        )
    }

    private fun checkNewBonus() {
        var emptyCells: MutableList<Coordinate> = mutableListOf()

        if (movements % bonus == 0 && !bonusCell) {
            for (row in 0 until 8) {
                val arr = boardPoints[row].mapIndexedNotNull { index, value ->
                    if (value == 0)
                        Coordinate(row, index)
                    else
                        null
                }

                if (arr.isNotEmpty()) {
                    emptyCells.addAll(arr)
                }
            }

            if (emptyCells.isEmpty()) return
            val randomCoordinate: Coordinate = emptyCells.random()
            val cell = board[randomCoordinate.x].getChildAt(randomCoordinate.y) as ImageView
            cell.setImageResource(R.drawable.star)
            cell.tag = "${cell.tag}bonus"
            boardPoints[randomCoordinate.x][randomCoordinate.y] = 3
            bonusCell = true
        }
    }

    private fun checkOptions(x: Int, y: Int, clear: Boolean = false) {
        checkMove(x, y, 1, 2, clear)
        checkMove(x, y, 1, -2, clear)
        checkMove(x, y, 2, 1, clear)
        checkMove(x, y, 2, -1, clear)
        checkMove(x, y, -1, 2, clear)
        checkMove(x, y, -1, -2, clear)
        checkMove(x, y, -2, 1, clear)
        checkMove(x, y, -2, -1, clear)

        if (checkMovement) {
            var actions = 0
            for (row in 0 until boardPoints.size) {
                for (col in 0 until boardPoints[row].size) {
                    if (boardPoints[row][col] == 0 || boardPoints[row][col] == 3) {
                        if (!clear) {
                            paintOptions(row, col)
                            actions++
                        } else {
                            clearOptions(row, col)
                            actions++
                            checkMovement = false
                        }
                    }
                }
            }

            if (actions == 0) {
                binding.lyMessage.visibility = LinearLayout.VISIBLE
                binding.tvIntroLevel.text = "You Win"
                binding.tvIntroLives.text = "${64 - movements}/64"
            }
        }
    }

    private fun checkMove(x: Int, y: Int, mov_x: Int, mov_y: Int, clear: Boolean) {
        val option_x = x + mov_x
        val option_y = y + mov_y

        if (option_x < 8 && option_y < 8 && option_x >= 0 && option_y >= 0) {
            if (clear) {
                clearOptions(option_x, option_y)
                return
            }

            if (boardPoints[option_x][option_y] == 0 || boardPoints[option_x][option_y] == 3) {
                options++
                paintOptions(option_x, option_y)

                boardPoints[option_x][option_y] = 2
            }
        }

    }

    private fun clearOptions(x: Int, y: Int) {
        val cell = board[x].getChildAt(y) as ImageView
        if (cell.background !is ColorDrawable) {
            cell.setBackgroundResource(if ((x + y) % 2 == 0)  R.color.white_cell else R.color.black_cell)
            if (boardPoints[x][y] != 1) {
                boardPoints[x][y] = 0
            }
            options = if (options > 0) options - 1 else 0
        }
    }

    private fun paintOptions(x: Int, y: Int) {
        val cell = board[x].getChildAt(y) as ImageView
        val currentBackground = cell.background as ColorDrawable
        val blackColor = ContextCompat.getColor(this, R.color.black_cell)

        cell.setBackgroundResource(if (currentBackground.color == blackColor) R.drawable.option_black else R.drawable.option_white)
    }

    private fun validatePosition(x: Int, y: Int): Boolean {
        val dif_x = x - currentCell.x
        val dif_y = y - currentCell.y

        if (dif_x == 1 && dif_y == 2) return  true
        if (dif_x == 1 && dif_y == -2) return  true
        if (dif_x == 2 && dif_y == 1) return  true
        if (dif_x == 2 && dif_y == -1) return  true
        if (dif_x == -1 && dif_y == 2) return  true
        if (dif_x == -1 && dif_y == -2) return  true
        if (dif_x == -2 && dif_y == 1) return  true
        if (dif_x == -2 && dif_y == -1) return  true

        if (checkMovement && (boardPoints[x][y] == 0 || boardPoints[x][y] == 3)) {
            bonusPoints--
            binding.tvBonusData.text = if (bonusPoints > 0) " + $bonusPoints" else ""
            return true
        }

        return false
    }

    private fun checkCell(x: Int, y: Int) {
        val currentCell = board[this.currentCell.x].getChildAt(this.currentCell.y) as ImageView
        val cellSelected = board[x].getChildAt(y) as ImageView
        val cellColor: ColorDrawable = cellSelected.background as ColorDrawable
        val previousColor = ContextCompat.getColor(this, R.color.previous_cell)
        val selectedColor = ContextCompat.getColor(this, R.color.selected_cell)

        if (cellColor.color == previousColor || cellColor.color == selectedColor)
            return

        currentCell.setBackgroundResource(R.color.previous_cell)
        cellSelected.setBackgroundResource(R.color.selected_cell)
        cellSelected.setImageResource(R.drawable.horse)
        this.currentCell = Coordinate(x, y)
        movements--
        boardPoints[x][y] = 1
        setHorseColor(cellSelected)
        binding.tvMovesNumber.text = "$movements"
    }

    private fun checkGameOver() {
        if (options == 0 ) {
            if (bonusPoints == 0) {
                binding.lyMessage.visibility = LinearLayout.VISIBLE
                binding.tvIntroLevel.text = "Game Over"
                binding.tvIntroLives.text = "${64 - movements}/64"
                binding.tvAction.text = "Reintentar"
                isGameOver = true
                stopTime()
            } else {
                checkMovement = true
                checkOptions(this.currentCell.x, this.currentCell.y)
            }
        }
    }

    private fun startNewGame() {
        hideMessage()
        initBoard(binding.tlBoard)
        growProgressionBonus()

        resetTime()
        startTime()
    }
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun startTime() {
        mHandler = Handler(Looper.getMainLooper())
        chronometer.run()
    }

    private fun stopTime() {
        mHandler?.removeCallbacks(chronometer)
    }

    private fun resetTime() {
        timeInSeconds = 0
        binding.tvTimeNumber.text = "00.00"
    }

    private var chronometer: Runnable = object : Runnable {
        override fun run() {
            try {
                timeInSeconds++
                updateStopWatchView(timeInSeconds)
            } finally {
                mHandler!!.postDelayed(this, 1000L)
            }
        }
    }

    private fun updateStopWatchView(seconds: Long) {
        val formattedTime = getFormattedStopWatch(seconds * 1000)
        binding.tvTimeNumber.text = formattedTime
    }

    private fun getFormattedStopWatch(ms: Long): String {
        var milliseconds = ms
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        return "${if(minutes<10) 0 else ""}$minutes:${if(seconds<10) 0 else ""}$seconds"
    }

    fun restartGame(view: View) {
        if (isGameOver) {
            startNewGame()
        }
    }
}