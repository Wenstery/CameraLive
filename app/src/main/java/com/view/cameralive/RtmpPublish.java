package com.view.cameralive;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Wenstery on 1/13/2017.
 */
public class RtmpPublish {
    private static final String TAG = "RtmpPublish";
    private static final String VCODEC = "video/avc";
    private static final String ACODEC = "audio/mp4a-latm";
    private static int VIDEO_WIDTH = 720;
    private static int VIDEO_HEIGHT = 480;
    private MediaCodec vcodec = null;
    private MediaCodec acodec = null;
    private static final int vbitrate = 512000;
    private static final int abitrate = 32000;
    private static final int framerate = 25;
    private static final int aSamplerate = 441000;
    private int aChannelCount;
    private MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();
    private long mPresentTimeUs;
    private RtmpWorker rtmpWorker;
    private AudioRecorder mAudioRecorder;
    private VideoGrabber mVideoGrabber;
    private boolean bRtmpInitFlag = false;

    public void setbRtmpInitFlag(boolean flag) {
        bRtmpInitFlag = flag;
    }

    public void initCamera(Activity act, SurfaceHolder holder) {
        mVideoGrabber.initCamera(act, holder);
    }

    public void releaseCamera() {
        mVideoGrabber.releaseCamera();
    }

    public void initPublish() {
        mAudioRecorder = new AudioRecorder();
        mVideoGrabber = new VideoGrabber();
    }

    public boolean startPublish(String rtmpUrl) {
        mAudioRecorder.start(aSamplerate);
        mAudioRecorder.setFrameCallback(new AudioRecorder.AudioFrameCallback() {
            @Override
            public void handleFrame(byte[] audio_data, int length) {
                if (bRtmpInitFlag) {
                    onEncodePcmData(audio_data, length);
                }
            }
        });
        aChannelCount = mAudioRecorder.getChannels();
        mVideoGrabber.setFrameCallback(new VideoGrabber.FrameCallback() {
            @Override
            public void handleFrame(byte[] yuv_image) {
                if (bRtmpInitFlag) {
                    onGetVideoData(yuv_image);
                }
            }
        });
        bRtmpInitFlag = startWorkerThread(rtmpUrl);
        return true;

    }

    public boolean startWorkerThread(String rtmpUrl) {
        if (rtmpUrl != null) {
            rtmpWorker = new RtmpWorker(aChannelCount, aSamplerate);
            if (rtmpWorker.startWorker(rtmpUrl) && startEncode()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void stopPublish() {
        mAudioRecorder.stop();
        stopEncode();
        rtmpWorker.stopWorker();
        Log.d(TAG, "stop publish!");

    }

    public boolean startEncode() {
        mPresentTimeUs = System.nanoTime() / 1000;
        try {
            vcodec = MediaCodec.createEncoderByType(VCODEC);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, VIDEO_WIDTH, VIDEO_HEIGHT);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, vbitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        vcodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        try {
            acodec = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        aChannelCount = mAudioRecorder.getChannels();
        MediaFormat audioFormat = MediaFormat.createAudioFormat(ACODEC, aSamplerate, aChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, abitrate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        acodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        vcodec.start();
        acodec.start();
        Log.d(TAG, "start encode!");
        return true;
    }

    public void stopEncode() {
        if (vcodec != null) {
            vcodec.stop();
            vcodec.release();
            vcodec = null;
        }
        if (acodec != null) {
            acodec.stop();
            acodec.release();
            acodec = null;
        }
        Log.d(TAG, "stop encode!");

    }

    public void onGetVideoData(byte[] videoData) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        byte[] yuv420sp = new byte[VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2];
        NV21ToNV12(videoData, yuv420sp, VIDEO_WIDTH, VIDEO_HEIGHT);
        onEncodeVideoFrame(yuv420sp, pts);
    }

    private void onEncodeVideoFrame(byte[] yuvData, long pts) {
        ByteBuffer[] inBuffers = vcodec.getInputBuffers();
        ByteBuffer[] outBuffers = vcodec.getOutputBuffers();

        int inBufferIndex = vcodec.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(yuvData, 0, yuvData.length);
            vcodec.queueInputBuffer(inBufferIndex, 0, yuvData.length, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = vcodec.dequeueOutputBuffer(vebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                rtmpWorker.onProcessH264(bb, vebi);
                vcodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }

    }

    public void onEncodePcmData(byte[] data, int size) {
        ByteBuffer[] inBuffers = acodec.getInputBuffers();
        ByteBuffer[] outBuffers = acodec.getOutputBuffers();

        int inBufferIndex = acodec.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(data, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            acodec.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = acodec.dequeueOutputBuffer(aebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                rtmpWorker.onProcessAac(bb, aebi);
                acodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

}
