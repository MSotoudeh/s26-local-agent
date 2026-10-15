package io.projectir.mobile;

import android.os.SystemClock;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Fixed public WAV -> sherpa-onnx native multilingual Whisper-base Int8 -> transcript.
 * Experimental benchmark only; never routes predicted text to actions.
 * Existing Vosk/Android Assistant pipeline remains the voice default.
 */
public final class SherpaWhisperRunner {
    public static final class Result {
        public final String transcript;
        public final long loadMs;
        public final long decodeMs;
        public final long totalMs;
        public final int sampleRate;
        public final int sampleCount;
        Result(String text, long load, long decode, long total, int hz, int samples) {
            transcript = text;
            loadMs = load;
            decodeMs = decode;
            totalMs = total;
            sampleRate = hz;
            sampleCount = samples;
        }
        public String describe() {
            return "M2_WHISPER_RESULT: transcript=" + transcript
                    + " | load_ms=" + loadMs + " decode_ms=" + decodeMs
                    + " total_ms=" + totalMs + " | input_hz=" + sampleRate
                    + " samples=" + sampleCount;
        }
    }

    private static class Wav {
        final float[] data;
        final int rate;
        Wav(float[] data, int rate) { this.data = data; this.rate = rate; }
    }
    private SherpaWhisperRunner() {}

    public static Result decodeFixedWav(File modelDir, File wav) throws Exception {
        Wav audio = readPcm16MonoWav(wav);
        if (audio.data.length < 1000 || audio.data.length > 16000 * 120) {
            throw new IllegalArgumentException("M2_INPUT_DURATION_LIMIT");
        }
        File encoder = new File(modelDir, "base-encoder.int8.onnx");
        File decoder = new File(modelDir, "base-decoder.int8.onnx");
        File tokens = new File(modelDir, "base-tokens.txt");
        if (!encoder.isFile() || !decoder.isFile() || !tokens.isFile()) throw new IllegalStateException("MODEL_ABSENT");
        OfflineModelConfig m = new OfflineModelConfig();
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(encoder.getAbsolutePath());
        whisper.setDecoder(decoder.getAbsolutePath());
        whisper.setLanguage("fa");
        whisper.setTask("transcribe");
        m.setWhisper(whisper);
        m.setTokens(tokens.getAbsolutePath());
        m.setModelType("whisper");
        m.setNumThreads(2);
        m.setProvider("cpu");
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setModelConfig(m);
        long begin = SystemClock.elapsedRealtime();
        OfflineRecognizer recognizer = null;
        OfflineStream stream = null;
        try {
            recognizer = new OfflineRecognizer(null, config);
            long loaded = SystemClock.elapsedRealtime();
            stream = recognizer.createStream();
            stream.acceptWaveform(audio.data, audio.rate);
            recognizer.decode(stream);
            long decoded = SystemClock.elapsedRealtime();
            String text = recognizer.getResult(stream).getText();
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalStateException("EMPTY_TRANSCRIPT");
            }
            return new Result(text, loaded - begin, decoded - loaded,
                    decoded - begin, audio.rate, audio.data.length);
        } finally {
            if (stream != null) stream.release();
            if (recognizer != null) recognizer.release();
        }
    }

    private static int word16(RandomAccessFile file) throws Exception {
        return (file.readUnsignedByte() | (file.readUnsignedByte() << 8));
    }
    private static long word32(RandomAccessFile file) throws Exception {
        return ((long) file.readUnsignedByte() | ((long) file.readUnsignedByte() << 8)
                | ((long) file.readUnsignedByte() << 16)
                | ((long) file.readUnsignedByte() << 24));
    }
    private static String fourcc(RandomAccessFile file) throws Exception {
        byte[] data = new byte[4];
        file.readFully(data);
        return new String(data, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static Wav readPcm16MonoWav(File file) throws Exception {
        if (!file.isFile() || file.length() > 10_000_000 || file.length() < 44) {
            throw new IllegalArgumentException("WAV_FILE_SIZE");
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (!"RIFF".equals(fourcc(raf))) throw new IllegalArgumentException("NOT_RIFF");
            word32(raf);
            if (!"WAVE".equals(fourcc(raf))) throw new IllegalArgumentException("NOT_WAVE");
            int hz = 0, channels = 0, bits = 0, format = 0;
            long dataOffset = -1, dataBytes = 0;
            while (raf.getFilePointer() + 8 <= raf.length()) {
                String kind = fourcc(raf);
                long n = word32(raf);
                long offset = raf.getFilePointer();
                if (n < 0 || offset + n > raf.length()) {
                    throw new IllegalArgumentException("INVALID_WAV_CHUNK");
                }
                if ("fmt ".equals(kind)) {
                    if (n < 16) throw new IllegalArgumentException("INVALID_WAV_FMT");
                    format = word16(raf);
                    channels = word16(raf);
                    hz = (int) word32(raf);
                    word32(raf);
                    word16(raf);
                    bits = word16(raf);
                } else if ("data".equals(kind)) {
                    dataOffset = offset; dataBytes = n;
                }
                raf.seek(offset + n + (n & 1L));
            }
            if (format != 1 || channels != 1 || bits != 16 || hz < 8000
                    || hz > 96000 || dataOffset < 0 || dataBytes < 2000
                    || dataBytes % 2 != 0 || dataBytes > 4_000_000) {
                throw new IllegalArgumentException("UNSUPPORTED_PUBLIC_WAV");
            }
            raf.seek(dataOffset);
            float[] result = new float[(int) (dataBytes / 2)];
            for (int i = 0; i < result.length; i++) {
                result[i] = ((short) word16(raf)) / 32768.0f;
            }
            return new Wav(result, hz);
        }
    }
}
