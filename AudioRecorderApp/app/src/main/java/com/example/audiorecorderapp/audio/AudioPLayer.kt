package com.example.audiorecorderapp.audio

import MediaListener
import android.content.Context
import android.media.MediaPlayer
import android.os.CountDownTimer
import androidx.core.net.toUri
import com.example.audiorecorderapp.TIMER_TICK_TIME
import java.io.File
import java.lang.ref.WeakReference

class AudioPLayer(private val context: WeakReference<Context>,
                  private val listener:WeakReference<MediaListener>,
                  private val timerTickCallBack:(time:Long) -> Unit) {

    private var player: MediaPlayer? = null
    private var countDownTimer: CountDownTimer? = null
    private var position:Int = 0
    private var playerDuration:Long = 0L


    fun setPlayerDuration(duration:Long) {
        this.playerDuration = duration
    }

    /**
     * before playing need to set duration
     * */
    fun play(file: File) {
        if (playerDuration <= 0L) throw IllegalStateException("playing file duration <= 0 ")
        context.get()?.let {
            MediaPlayer.create(it, file.toUri()).apply {
                player = this
                start()
                startTimer(getPLayerDuration())
                listener.get()?.changePlayerState(MediaState.PLAYING)
            }
        }
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
        stopTimer()
        listener.get()?.changePlayerState(MediaState.STOP_PLAYING)
    }


    fun seekToPosition(position:Int) {
        this.position = position
    }

    fun seekToRunTime(position: Int) {
        stopTimer()
        this.position = position
        player?.seekTo(position)
        player?.start()
        startTimer(getPLayerDuration() - position)
    }

    fun getPLayerDuration(): Long {
        return playerDuration
    }

    fun pauseRunTime() {
        stopTimer()
        player?.pause()
    }

    fun pause() {
        stopTimer()
        player?.pause()
        listener.get()?.changePlayerState(MediaState.PAUSE_PLAYING)
    }

    /**
     * before playing need to set duration
     * */
    fun resume(file: File) {
        if (playerDuration <= 0L) throw IllegalStateException("playing file duration <= 0 ")

        if (player == null) {
            context.get()?.let {
                MediaPlayer.create(it, file.toUri()).apply {
                    player = this
                    if (position > getPLayerDuration()) {
                        position = 0
                    }
                    player?.seekTo(position)
                    start()
                    startTimer(getPLayerDuration() - position)
                    listener.get()?.changePlayerState(MediaState.PLAYING)
                }
            }
        } else {
            if (position > getPLayerDuration()) {
                position = 0
            }
            player?.seekTo(position)
            player?.start()
            startTimer(getPLayerDuration() - position)
            listener.get()?.changePlayerState(MediaState.PLAYING)
        }
    }

    private fun startTimer(timerDuration:Long) {
        stopTimer()
        countDownTimer = object :CountDownTimer(timerDuration, TIMER_TICK_TIME) {
            override fun onTick(millisUntilFinished: Long) {
                timerTickCallBack(getPLayerDuration() - millisUntilFinished)
            }

            override fun onFinish() {
                timerTickCallBack(0L)
                stop()
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

}