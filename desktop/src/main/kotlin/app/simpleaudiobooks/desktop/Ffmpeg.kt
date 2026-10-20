package app.simpleaudiobooks.desktop

import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AV_DICT_IGNORE_SUFFIX
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_dict_get
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber

/** Zugang zu FFmpeg (über JavaCPP): Dateien untersuchen, ohne sie abzuspielen. */
object Ffmpeg {

    private val ready by lazy {
        av_log_set_level(AV_LOG_QUIET)
        true
    }

    /** Lädt die nativen Bibliotheken; beim ersten Mal entpackt JavaCPP sie in seinen Zwischenspeicher. */
    fun init(): Boolean = ready

    /**
     * Lädt auch die Teile für die Wiedergabe (Dekoder, Filter, Umrechnung). Beim ersten Start dauert das Auspacken
     * einige Sekunden; im Hintergrund erledigt, beginnt das erste Abspielen ohne Wartezeit.
     */
    fun warmUp() {
        init()
        FFmpegFrameGrabber.tryLoad()
        FFmpegFrameFilter.tryLoad()
    }

    data class Info(val durationMs: Long, val tags: Map<String, String>, val picture: ByteArray?)

    /** Dauer, Tags (Schlüssel klein geschrieben) und auf Wunsch das eingebettete Titelbild. */
    fun probe(path: String, withPicture: Boolean = false): Info? {
        init()
        val ctx = AVFormatContext(null)
        if (avformat_open_input(ctx, BytePointer(path, "UTF-8"), null, null) < 0) return null
        try {
            avformat_find_stream_info(ctx, null as PointerPointer<*>?)
            var duration = if (ctx.duration() > 0) ctx.duration() / 1000 else 0L
            val tags = LinkedHashMap<String, String>()
            readDict(ctx.metadata(), tags)
            var picture: ByteArray? = null
            for (i in 0 until ctx.nb_streams()) {
                val st = ctx.streams(i)
                readDict(st.metadata(), tags)
                if (withPicture && picture == null && (st.disposition() and AV_DISPOSITION_ATTACHED_PIC) != 0) {
                    val pkt = st.attached_pic()
                    val size = pkt.size()
                    if (size > 0) picture = ByteArray(size).also { pkt.data().get(it) }
                }
                if (duration <= 0 && st.codecpar()?.codec_type() == AVMEDIA_TYPE_AUDIO && st.duration() > 0) {
                    val tb = st.time_base()
                    duration = st.duration() * 1000 * tb.num() / tb.den().coerceAtLeast(1)
                }
            }
            return Info(duration, tags, picture)
        } finally {
            avformat_close_input(ctx)
        }
    }

    private fun readDict(dict: AVDictionary?, into: MutableMap<String, String>) {
        if (dict == null || dict.isNull) return
        var prev: AVDictionaryEntry? = null
        while (true) {
            val e = av_dict_get(dict, "", prev, AV_DICT_IGNORE_SUFFIX) ?: break
            if (e.isNull) break
            val key = e.key()?.getString("UTF-8")?.lowercase()
            val value = e.value()?.getString("UTF-8")
            if (!key.isNullOrBlank() && !value.isNullOrBlank()) into.putIfAbsent(key, value)
            prev = e
        }
    }
}
