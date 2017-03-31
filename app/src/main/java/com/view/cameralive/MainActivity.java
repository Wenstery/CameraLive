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
    private String rtmpUrl = "rtmp://192.168...";
    private ToggleButton mTbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPublisher = new RtmpPublish();
        mPublisher.initPublish();
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
                mPublisher.initCamera(MainActivity.this, mSurfaceHolder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mPublisher.releaseCamera();

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
                            mPublisher.startPublish(mUrlText.getText().
                                    toString());
                        }
                    }.start();

                } else {
                    mPublisher.setbRtmpInitFlag(false);
                    mPublisher.stopPublish();
                    mPublisher = null;

                }
            }
        });

    }

}
