package com.example.audiorecorderapp

import AudioRecorder
import MediaListener
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audiorecorderapp.audio.AudioPLayer
import com.example.audiorecorderapp.audio.MediaState
import com.example.audiorecorderapp.audio.RecordingData
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max

const val MAX_DURATION_MS = 20000
const val TIMER_TICK_TIME = 100L

class MainActivity : ComponentActivity() , MediaListener {
    private val maxReportableAmp = 22760f
    private val splashViewModel: SplashViewModel by viewModels()

    private val recorder by lazy {
        AudioRecorder(
            WeakReference(this),
            WeakReference(this),
            ::recordingCallBack
        )
    }

    private val player by lazy {
        AudioPLayer(
            WeakReference(this),
            WeakReference(this),
            ::playerTimerTick
        )
    }

    private var file: File? = null
    private val textPaint = TextPaint()
    private val textRec = Rect()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecording()
            } else {
                Toast.makeText(this, "you have no permission to record audio", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textPaint.setColor(android.graphics.Color.WHITE)
        textPaint.textSize = 32f
        setContent {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    MainScreen()
                }
        }
    }


    @ExperimentalMaterialApi
    @Composable
    fun MainScreen() {
        val barWidth = 8.dp.value
        val spaceBetweenBar = 4.dp.value
        val recordingTimerText = splashViewModel.recordingTimerFlow.collectAsState()
        val playingTime = splashViewModel.playingTimerFlow
        val mediaState = splashViewModel.mediaStateFlow.collectAsState()
        val recordingDataState = splashViewModel.recordingAmplAndTimes.collectAsState()
        val dragPosition = remember { mutableStateOf(0f) }

        LaunchedEffect(true) {
            playingTime.collect {
                if (dragPosition.value != 0f) {
                    if (recordingDataState.value.list.isNotEmpty()) {
                        val amplSize =
                            recordingDataState.value.list.size * (barWidth + spaceBetweenBar) + barWidth + spaceBetweenBar
                        val d = (amplSize / player.getPLayerDuration()) * TIMER_TICK_TIME
                        if (dragPosition.value - d >= 0) {
                            dragPosition.value -= d
                        } else {
                            dragPosition.value = 0f
                        }
                    }
                }
            }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    color = Color.Black,
                    text = splashViewModel.calculateTimerTime(recordingTimerText.value),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier
                    .background(Color.Gray)
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(true) {
                        detectDragGestures(
                            onDragStart = {
                                if (mediaState.value == MediaState.PLAYING) {
                                    player.pauseRunTime()
                                }
                            },
                            onDragEnd = {
                                var positionItem: RecordingData? = null

                                val with = size.width

                                recordingDataState.value.list.forEachIndexed { index, item ->
                                    val startX =
                                        dragPosition.value + with / 2 - (recordingDataState.value.list.size - index) * (barWidth + spaceBetweenBar)
                                    if (startX < with / 2 + barWidth) {
                                        if (positionItem == null) {
                                            positionItem = item
                                        } else if (positionItem!!.time < item.time) {
                                            positionItem = item
                                        }
                                    }
                                }
                                if (mediaState.value == MediaState.PLAYING) {
                                    player.seekToRunTime(
                                        positionItem?.time?.toInt()
                                            ?: recordingTimerText.value.toInt()
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (mediaState.value != MediaState.RECORDING) {
                                    val amplSize =
                                        recordingDataState.value.list.size * (barWidth + spaceBetweenBar) + barWidth + spaceBetweenBar
                                    val distance = dragPosition.value + dragAmount.x
                                    if (abs(distance) < amplSize) {
                                        dragPosition.value = max(distance, 0f)
                                    }
                                }
                            })
                    }) {


                    recordingDataState.value.list.forEachIndexed { index, item ->
                        val with = size.width
                        val startX = dragPosition.value + with / 2 - (recordingDataState.value.list.size - index) * (barWidth + spaceBetweenBar)
                        if (startX < with / 2 + barWidth) {
                            splashViewModel.changeTimerTime(item.time)
                        }
                        if (index == 0 || item.time % 1000 == 0L) {
                            drawIntoCanvas {
                                val text = splashViewModel.calculateAmplTime(item.time)
                                textPaint.getTextBounds(text, 0, text.length, textRec)
                                val textX = (startX) - textRec.width() / 2

                                if (index == 0) {
                                    it.nativeCanvas.drawText(
                                        splashViewModel.calculateAmplTime(item.time),
                                        textX,
                                        0f,
                                        textPaint
                                    )
                                    it.nativeCanvas.drawText(
                                        splashViewModel.calculateAmplTime(item.time + 1000),
                                        textX + (10 * (barWidth + spaceBetweenBar)),
                                        0f,
                                        textPaint
                                    )
                                } else {
                                    it.nativeCanvas.drawText(
                                        splashViewModel.calculateAmplTime(item.time + 1000),
                                        textX + (10 * (barWidth + spaceBetweenBar)),
                                        0f,
                                        textPaint
                                    )
                                }
                            }
                        }

                        val baseLine = size.height / 2

                        val amplH = calculateAmplitudeHeight(item.amplitude).dp.toPx()

                        val startY = baseLine + (amplH / 2)
                        val stopY = startY - amplH
                        val startOffset = Offset(startX, startY)
                        val endOffset = Offset(startX, stopY)


                        if (startX < size.width / 2) {
                            drawLine(
                                color = Color.White,
                                strokeWidth = barWidth,
                                start = startOffset,
                                end = endOffset
                            )
                        } else {
                            drawLine(
                                color = Color.Yellow,
                                strokeWidth = barWidth,
                                start = startOffset,
                                end = endOffset
                            )
                        }
                    }

                    if (recordingDataState.value.list.size > 0) {
                        val centerLineStartOffset =
                            Offset(size.width / 2, textRec.height().toFloat())
                        val centerLineEndOffset =
                            Offset(size.width / 2, size.height - textRec.height().toFloat())
                        drawLine(
                            color = Color.Red,
                            strokeWidth = barWidth,
                            start = centerLineStartOffset,
                            end = centerLineEndOffset
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .wrapContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {

                        val painter = if (mediaState.value == MediaState.PLAYING) {
                            painterResource(id = R.drawable.pause)
                        } else {
                            painterResource(id = R.drawable.play)
                        }
                        Image(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable {
                                    if (mediaState.value == MediaState.STOP_RECORDING || mediaState.value == MediaState.STOP_PLAYING) {
                                        var position: Long? = null

                                        val recordingDuration =
                                            if (recordingDataState.value.list.isNotEmpty()) {
                                                recordingDataState.value.list.last().time
                                            } else {
                                                null
                                            }

                                        if (dragPosition.value == 0f || recordingTimerText.value >= (recordingDuration
                                                ?: 0L)
                                        ) {
                                            val amplSize =
                                                recordingDataState.value.list.size * (barWidth + spaceBetweenBar) + barWidth + spaceBetweenBar
                                            dragPosition.value = amplSize
                                        } else {
                                            position = recordingTimerText.value
                                        }

                                        if (position == null) {
                                            file?.let {
                                                player.setPlayerDuration(recordingDuration ?: 0L)
                                                player.play(it)
                                            }
                                        } else {
                                            file?.let {
                                                player.setPlayerDuration(recordingDuration ?: 0L)
                                                player.seekToPosition(position.toInt())
                                                player.resume(it)
                                            }
                                        }

                                    } else if (mediaState.value == MediaState.PLAYING) {
                                        player.pause()
                                    } else if (mediaState.value == MediaState.PAUSE_PLAYING) {
                                        val position = recordingTimerText.value
                                        file?.let {
                                            val recordingDuration =
                                                if (recordingDataState.value.list.isNotEmpty()) {
                                                    recordingDataState.value.list.last().time
                                                } else {
                                                    null
                                                }
                                            player.setPlayerDuration(recordingDuration ?: 0L)
                                            player.seekToPosition(position.toInt())
                                            player.resume(it)
                                        }
                                    }
                                },
                            painter = painter,
                            contentDescription = null
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_brightness),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .clip(CircleShape)
                        )

                        val tint: Color

                        val painter = if (mediaState.value == MediaState.RECORDING) {
                            tint = Color.White
                            painterResource(id = R.drawable.pause)
                        } else {
                            tint = Color.Red
                            painterResource(id = R.drawable.baseline_brightness)
                        }

                        Icon(
                            painter = painter,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (mediaState.value == MediaState.RECORDING) {
                                        recorder.stop()
                                    } else if (mediaState.value == MediaState.NOTHING ||
                                        mediaState.value == MediaState.STOP_RECORDING ||
                                        mediaState.value == MediaState.PAUSE_PLAYING ||
                                        mediaState.value == MediaState.STOP_PLAYING
                                    ) {
                                        splashViewModel.resetRecordingData()
                                        dragPosition.value = 0f
                                        askPermissionIfNeededAndStartRecording()
                                    }
                                }
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    private fun startRecording() {
        val existFile = File(cacheDir, "audio.mp3")
        if (existFile.exists()) {
            existFile.delete()
        }
        File(cacheDir, "audio.mp3").also {
            recorder.start(it)
            file = it
        }
    }

    private fun askPermissionIfNeededAndStartRecording() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun playerTimerTick(mills: Long) {
        splashViewModel.setPlayingTime(mills)
    }

    override fun changePlayerState(state: MediaState) {
        splashViewModel.changeMediaState(state)
    }

    private fun recordingCallBack(recordingData: RecordingData) {
        splashViewModel.updateAmplitudes(recordingData)
    }

    private fun calculateAmplitudeHeight(amplitude: Int): Int {
        return if (amplitude >= maxReportableAmp) {
            120 /*experimental size*/
        } else {
            val percent = (amplitude * 100) / maxReportableAmp
            val result = (120 * percent.toInt()) / 100
            if (result < 12) {
                12
            } else {
                result
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {

}

