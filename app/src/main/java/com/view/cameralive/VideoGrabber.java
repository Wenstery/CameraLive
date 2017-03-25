package com.view.cameralive;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.List;

import static android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX;

/**
 * Created by Wenstery on 11/7/2016.
 */
public class VideoGrabber {
    private static final String TAG = "VideoGrabber";
    private Camera camera;
    List<Camera.Size> supportedPreviewSizes;
    List<int[]> supportedPreviewFpsRange;
    int[] fpsRange = {25 * 1000, 45 * 1000};
    Camera.Size mPreviewSize;
    private FrameCallback frameCallback;

    public void setFrameCallback(FrameCallback callback) {
        frameCallback = callback;
    }

    public void initCamera(Activity act, SurfaceHolder holder) {
        try {
            Log.d(TAG, "init camera");
            setCamera(getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT));
            if (camera != null) {
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                parameters.setPreviewFormat(ImageFormat.NV21);
                camera.setParameters(parameters);
                setCameraDisplayOrientation(act, Camera.CameraInfo.CAMERA_FACING_BACK, camera);
                camera.setPreviewDisplay(holder);
                camera.startPreview();
                Log.d(TAG, "start preview");
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    public Camera getCamera(int cameraType) {
        if (camera == null) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == cameraType) {
                    try {
                        camera = Camera.open(i);
                    } catch (RuntimeException e) {
                        Log.e(TAG, String.format("Couldn't open camera type '%d'.", cameraType), e);
                    }
                }
            }

            if (camera == null) {
                camera = Camera.open();
            }
        }
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;

        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            for (Camera.Size s : supportedPreviewSizes) {
                if (s.width > 320 && s.width <= 720) {
                    mPreviewSize = s;
                    break;
                }
            }
            Log.d(TAG, "mPreviewSize = " + mPreviewSize.width + " " + mPreviewSize.height);
            supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();

            for (int[] range : supportedPreviewFpsRange
                    ) {
                if (range[PREVIEW_FPS_MIN_INDEX] >= 25 * 1000 && range[PREVIEW_FPS_MAX_INDEX] <= 45 * 1000) {
                    fpsRange = range;
                    Log.d(TAG, "fpsRange: " + range[PREVIEW_FPS_MIN_INDEX] + ',' + range[PREVIEW_FPS_MAX_INDEX]);
                    break;
                }
            }
        }
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void setBufferCallback() {
        Camera.Parameters params = camera.getParameters();
        int bufferSize = mPreviewSize.width * mPreviewSize.height * ImageFormat.getBitsPerPixel(
                params.getPreviewFormat());
        camera.addCallbackBuffer(new byte[bufferSize]);

        camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] yuv_image, Camera camera) {
                if (frameCallback != null) {
                    frameCallback.handleFrame(yuv_image);
                }
                camera.addCallbackBuffer(yuv_image);
            }
        });
    }

    public interface FrameCallback {
        void handleFrame(byte[] yuv_image);
    }
}
