package com.example.audiorecorderapp


import com.example.audiorecorderapp.audio.MediaState
import com.example.audiorecorderapp.audio.RecordingData
import com.example.audiorecorderapp.audio.RecordingDataState
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class SplashViewModel (
) : ViewModel(){

    private val _mediaStateFlow = MutableStateFlow(MediaState.NOTHING)
    val mediaStateFlow = _mediaStateFlow.asStateFlow()

    private val _recordingTimerFlow = MutableStateFlow(0L)
    val recordingTimerFlow = _recordingTimerFlow.asStateFlow()

    private val _playingTimerFlow = MutableStateFlow(0L)
    val playingTimerFlow = _playingTimerFlow.asStateFlow()

    private val _recordingAmplAndTimes = MutableStateFlow(RecordingDataState())
    val recordingAmplAndTimes = _recordingAmplAndTimes.asStateFlow()



    private val formatter: NumberFormat = DecimalFormat("#00.00")

    fun changeMediaState(state: MediaState) = viewModelScope.launch {
        _mediaStateFlow.emit(state)
    }

    fun changeTimerTime(time: Long) = viewModelScope.launch {
        _recordingTimerFlow.emit(time)
    }

    fun updateAmplitudes(amplitude: RecordingData) = viewModelScope.launch {
        val newData = RecordingDataState()
        newData.list = _recordingAmplAndTimes.value.list
        newData.list.add(amplitude)
        _recordingAmplAndTimes.emit(newData)
    }

    fun setPlayingTime(time:Long) = viewModelScope.launch {
        _playingTimerFlow.emit(time)
    }

    fun resetRecordingData()  = viewModelScope.launch {
        _recordingAmplAndTimes.emit(RecordingDataState())
        _recordingTimerFlow.emit(0)
        _mediaStateFlow.emit(MediaState.NOTHING)
    }

    /**
     * this function only supports minute and second registers.
     * If the input parameter is long, one hour, this may not work correctly.
     * */
    fun calculateTimerTime(secondInMills: Long): String {
        val result: String
        if (secondInMills >= 60000/*one minute*/) {
            val minute = secondInMills / 60000
            val minuteString = if (minute >= 10) {
                "$minute"
            } else {
                "0$minute"
            }
            val sec = secondInMills % 60000
            val secondString = formatter.format(sec / 1000.00)
            result = "$minuteString:$secondString"
        } else {
            val minuteString = "00"
            val sec = secondInMills / 1000.00
            val secondString = formatter.format(sec)
            result = "$minuteString:$secondString"
        }
        return result
    }

    fun calculateAmplTime(secondInMills: Long): String {
        val result: String
        if (secondInMills >= 60000/*one minute*/) {
            val minute = secondInMills / 60000
            val minuteString = if (minute >= 10) {
                "$minute"
            } else {
                "0$minute"
            }
            val secMM = secondInMills % 60000
            val secondString = if (secMM > 0) {
                val sec = secMM / 1000
                if (sec>=10) {
                    "$sec"
                } else {
                    "0$sec"
                }
            } else {
                "00"
            }

            result = "$minuteString:$secondString"
        } else {
            val minuteString = "00"
            val sec = secondInMills / 1000
            val secondString = if (sec >= 10) {
                "$sec"
            } else {
                "0$sec"
            }
            result = "$minuteString:$secondString"
        }
        return result
    }
}