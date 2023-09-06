
import com.example.audiorecorderapp.audio.MediaState
import com.example.audiorecorderapp.audio.RecordingData
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import com.example.audiorecorderapp.MAX_DURATION_MS
import com.example.audiorecorderapp.TIMER_TICK_TIME
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference

class AudioRecorder(private val context: WeakReference<Context>,
                    private val listener:WeakReference<MediaListener>,
                    private val amplitudeCallBack:(recordingData: RecordingData) -> Unit) {
    private var recorder: MediaRecorder? = null
    private var countDownTimer: CountDownTimer? = null

    private fun createRecorder() : MediaRecorder? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.get()?.let {
            MediaRecorder(it)
        }
    } else {
        MediaRecorder()
    }


    fun start(file: File) {
        createRecorder()?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(FileOutputStream(file).fd)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setMaxDuration(MAX_DURATION_MS)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("LOG_TAG", "prepare() failed")
            }

            start()
            startTimer()
            recorder = this
            listener.get()?.changePlayerState(MediaState.RECORDING)
        }
    }

    fun stop() {
        recorder?.stop()
        recorder?.reset()
        recorder?.release()
        recorder = null
        stopTimer()
        listener.get()?.changePlayerState(MediaState.STOP_RECORDING)
    }

    private fun startTimer() {
        stopTimer()
        countDownTimer = object : CountDownTimer(MAX_DURATION_MS.toLong(), TIMER_TICK_TIME) {
            override fun onTick(millisUntilFinished: Long) {

                recorder?.maxAmplitude?.let {
                    var time = if (millisUntilFinished < TIMER_TICK_TIME) {
                        MAX_DURATION_MS.toLong()
                    } else {
                        MAX_DURATION_MS.toLong() - millisUntilFinished
                    }
                    if (time > 1000 && time % 1000 < TIMER_TICK_TIME) {
                        time = time - time % 1000
                    } else if (time < TIMER_TICK_TIME) {
                        time = 0
                    }
                    amplitudeCallBack(RecordingData(time, it))
                }
            }

            override fun onFinish() {
                stop()
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }
}