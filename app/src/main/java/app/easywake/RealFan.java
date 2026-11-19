package app.easywake;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

/** Unpacks the built-in real fan recording (res/raw/real_fan.ogg) into plain samples. */
final class RealFan {
    private static short[] cached;

    private RealFan() {
    }

    /** The recording as mono samples, 44,100 a second, or null if it can't be read. */
    static synchronized short[] load(Context c) {
        if (cached != null) return cached;
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            AssetFileDescriptor fd = c.getResources().openRawResourceFd(R.raw.real_fan);
            ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            ex.selectTrack(0);
            MediaFormat fmt = ex.getTrackFormat(0);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            short[] out = new short[NoiseGen.SAMPLE_RATE * 62];
            int n = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            long giveUp = System.currentTimeMillis() + 15000;
            while (!outDone && System.currentTimeMillis() < giveUp) {
                if (!inDone) {
                    int i = codec.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        ByteBuffer b = codec.getInputBuffer(i);
                        int size = b == null ? -1 : ex.readSampleData(b, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(i, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 10000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = codec.getOutputFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (o >= 0) {
                    ByteBuffer b = codec.getOutputBuffer(o);
                    if (b != null && info.size > 0) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        ShortBuffer s = b.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int ch = Math.max(1, channels);
                        while (s.remaining() >= ch) {
                            int sum = 0;
                            for (int k = 0; k < ch; k++) sum += s.get();
                            if (n == out.length) out = Arrays.copyOf(out, out.length * 2);
                            out[n++] = (short) (sum / ch);
                        }
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                }
            }
            if (n < NoiseGen.SAMPLE_RATE * 40) return null;
            cached = Arrays.copyOf(out, n);
            return cached;
        } catch (Exception e) {
            return null;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    // Not started.
                }
                codec.release();
            }
            ex.release();
        }
    }
}
