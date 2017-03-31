package com.view.cameralive;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Created by Wenstery on 2/27/2017.
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private Thread thread;
    private boolean cancel = false;
    private int samplerate;
    private AudioFrameCallback frameCallback;
    private int aChannelConfig;
    private byte[] mPcmBuffer;

    public void setFrameCallback(AudioFrameCallback callback) {
        frameCallback = callback;
    }

    public void start(int samplerate) {
        Log.d(TAG, "start");
        this.samplerate = samplerate;
        cancel = false;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                recordThread();
            }
        });
        thread.start();
    }

    public void recordThread() {
        Log.d(TAG, "recordThread");

        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        aChannelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        int bufferSize = 2 * AudioRecord.getMinBufferSize(samplerate, aChannelConfig, audioEncoding);
        AudioRecord recorder = chooseAudioRecord(bufferSize);
        if (recorder != null) {
            recorder.startRecording();
        } else {
            Log.e(TAG, "audio recoder is null");
            cancel = true;
        }
        mPcmBuffer = new byte[Math.min(4096, bufferSize)];
        while (!cancel) {
            int bufferReadResult = recorder.read(mPcmBuffer, 0, mPcmBuffer.length);
            if (bufferReadResult > 0) {
                frameCallback.handleFrame(mPcmBuffer, bufferReadResult);
            } else if (bufferReadResult < 0) {
                Log.e(TAG, "read audio data: " + bufferReadResult);
            }
        }
        recorder.stop();
    }

    public void stop() {
        Log.d(TAG, "stop");

        cancel = true;
        try {
            thread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
        }
    }

    public interface AudioFrameCallback {
        void handleFrame(byte[] audio_data, int length);
    }

    private AudioRecord chooseAudioRecord(int size) {
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.MIC, samplerate,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, size);
        return mic;
    }

    public int getChannels() {
        return aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
    }


}
