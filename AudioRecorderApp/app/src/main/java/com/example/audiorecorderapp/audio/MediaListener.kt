import com.example.audiorecorderapp.audio.MediaState

interface MediaListener {
    fun changePlayerState(state: MediaState)
}