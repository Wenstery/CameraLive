package com.view.cameralive;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ToggleButton;

/**
 * Created by Wenstery on 10/7/2016.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private EditText mUrlText;
    private Button mCamera;
    private RtmpPublish mPublisher;
    private AudioRecorder mAudioRecorder;
    private static final int AUDIOSAMPLE = 44100;
    private String rtmpUrl = "rtmp://192.168...";
    private boolean bRtmpInitFlag = false;
    private ToggleButton mTbutton;
    private VideoGrabber mVideoGrabber = new VideoGrabber();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback2() {
            @Override
            public void surfaceRedrawNeeded(SurfaceHolder holder) {

            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "surfaceCreated:");

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.i(TAG, "surfaceChanged: ");
                mVideoGrabber.initCamera(MainActivity.this, mSurfaceHolder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mVideoGrabber.releaseCamera();

            }
        });

        mCamera = (Button) findViewById(R.id.cameraContol);
        mCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }

        });

        mUrlText = (EditText) findViewById(R.id.url);
        mUrlText.setText(rtmpUrl);
        mTbutton = (ToggleButton) findViewById(R.id.toggleLive);
        mTbutton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    new Thread() {
                        @Override
                        public void run() {
                            mPublisher = new RtmpPublish();
                            mAudioRecorder = new AudioRecorder();
                            mAudioRecorder.start(AUDIOSAMPLE);
                            mAudioRecorder.setFrameCallback(new AudioRecorder.AudioFrameCallback() {
                                @Override
                                public void handleFrame(byte[] audio_data, int length) {
                                    if (bRtmpInitFlag) {
                                        mPublisher.onEncodePcmData(audio_data, length);
                                    }
                                }
                            });
                            mVideoGrabber.setFrameCallback(new VideoGrabber.FrameCallback() {
                                @Override
                                public void handleFrame(byte[] yuv_image) {
                                    if (bRtmpInitFlag) {
                                        mPublisher.onGetVideoData(yuv_image);
                                    }
                                }
                            });
                            mVideoGrabber.setBufferCallback();
                            bRtmpInitFlag = mPublisher.startPublish(mUrlText.getText().
                                    toString(), mAudioRecorder.getChannels(), mAudioRecorder.getSamplerate());
                        }
                    }.start();

                } else {
                    bRtmpInitFlag = false;
                    mPublisher.stopPublish();
                    mPublisher = null;

                }
            }
        });

    }

}
