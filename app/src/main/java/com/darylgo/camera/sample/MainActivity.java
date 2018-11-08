package com.darylgo.camera.sample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Handler.Callback {

    private static final int MSG_OPEN_CAMERA = 1;
    private static final int MSG_CLOSE_CAMERA = 2;
    private static final int MSG_SET_PREVIEW_SIZE = 3;
    private static final int MSG_SET_PREVIEW_SURFACE = 4;
    private static final int MSG_START_PREVIEW = 5;
    private static final int MSG_STOP_PREVIEW = 6;

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Nullable
    private HandlerThread mCameraThread = null;

    @Nullable
    private Handler mCameraHandler = null;

    @Nullable
    private Camera.CameraInfo mFrontCameraInfo = null;
    private int mFrontCameraId = -1;

    @Nullable
    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;

    @Nullable
    private Camera mCamera;
    private int mCameraId = -1;
    private Camera.CameraInfo mCameraInfo;

    @Nullable
    private SurfaceHolder mPreviewSurface;
    private int mPreviewSurfaceWidth;
    private int mPreviewSurfaceHeight;

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_OPEN_CAMERA:
                openCamera(msg.arg1);
                break;
            case MSG_CLOSE_CAMERA:
                closeCamera();
                break;
            case MSG_SET_PREVIEW_SIZE:
                int shortSide = msg.arg1;
                int longSide = msg.arg2;
                setPreviewSize(shortSide, longSide);
                break;
            case MSG_SET_PREVIEW_SURFACE:
                SurfaceHolder previewSurface = (SurfaceHolder) msg.obj;
                setPreviewSurface(previewSurface);
                break;
            case MSG_START_PREVIEW:
                startPreview();
                break;
            case MSG_STOP_PREVIEW:
                stopPreview();
                break;
            default:
                throw new IllegalArgumentException("Illegal message: " + msg.what);
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startCameraThread();
        initCameraInfo();

        SurfaceView cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.getHolder().addCallback(new PreviewSurfaceCallback());

        Button switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setOnClickListener(new OnSwitchCameraButtonClickListener());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 动态权限检查
        if (!isRequiredPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS_CODE);
        } else if (mCameraHandler != null) {
            mCameraHandler.obtainMessage(MSG_OPEN_CAMERA, getCameraId(), 0).sendToTarget();
        }
    }

    /**
     * 获取要开启的相机 ID，优先开启前置。
     */
    private int getCameraId() {
        if (hasFrontCamera()) {
            return mFrontCameraId;
        } else if (hasBackCamera()) {
            return mBackCameraId;
        } else {
            throw new RuntimeException("No available camera id found.");
        }
    }

    /**
     * 切换前后置时切换ID
     */
    private int switchCameraId() {
        if (mCameraId == mFrontCameraId && hasBackCamera()) {
            return mBackCameraId;
        } else if (mCameraId == mBackCameraId && hasFrontCamera()) {
            return mFrontCameraId;
        } else {
            throw new RuntimeException("No available camera id to switch.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraHandler != null) {
            mCameraHandler.removeMessages(MSG_OPEN_CAMERA);
            mCameraHandler.sendEmptyMessage(MSG_CLOSE_CAMERA);
        }
    }

    /**
     * 判断我们需要的权限是否被授予，只要有一个没有授权，我们都会返回 false。
     *
     * @return true 权限都被授权
     */
    private boolean isRequiredPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Window window = getWindow();
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper(), this);
    }

    private void stopCameraThread() {
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
        }
        mCameraThread = null;
        mCameraHandler = null;
    }

    /**
     * 初始化摄像头信息。
     */
    private void initCameraInfo() {
        int numberOfCameras = Camera.getNumberOfCameras();// 获取摄像头个数
        for (int cameraId = 0; cameraId < numberOfCameras; cameraId++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // 后置摄像头信息
                mBackCameraId = cameraId;
                mBackCameraInfo = cameraInfo;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                // 前置摄像头信息
                mFrontCameraId = cameraId;
                mFrontCameraInfo = cameraInfo;
            }
        }
    }

    /**
     * 开启指定摄像头
     */
    @WorkerThread
    private void openCamera(int cameraId) {
        Camera camera = mCamera;
        if (camera != null) {
            throw new RuntimeException("You must close previous camera before open a new one.");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mCamera = Camera.open(cameraId);
            mCameraId = cameraId;
            mCameraInfo = cameraId == mFrontCameraId ? mFrontCameraInfo : mBackCameraInfo;
            Log.d(TAG, "Camera[" + cameraId + "] has been opened.");
            assert mCamera != null;
            mCamera.setDisplayOrientation(getCameraDisplayOrientation(mCameraInfo));
        }
    }

    /**
     * 获取预览画面要校正的角度。
     */
    private int getCameraDisplayOrientation(Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
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
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * 关闭相机。
     */
    @WorkerThread
    private void closeCamera() {
        Camera camera = mCamera;
        mCamera = null;
        if (camera != null) {
            camera.release();
            mCameraId = -1;
            mCameraInfo = null;
        }
    }

    /**
     * 根据指定的尺寸要求设置预览尺寸，我们会同时考虑指定尺寸的比例和大小。
     *
     * @param shortSide 短边长度
     * @param longSide  长边长度
     */
    @WorkerThread
    private void setPreviewSize(int shortSide, int longSide) {
        Camera camera = mCamera;
        if (camera != null && shortSide != 0 && longSide != 0) {
            float aspectRatio = (float) longSide / shortSide;
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            for (Camera.Size previewSize : supportedPreviewSizes) {
                if ((float) previewSize.width / previewSize.height == aspectRatio && previewSize.height <= shortSide && previewSize.width <= longSide) {
                    parameters.setPreviewSize(previewSize.width, previewSize.height);
                    camera.setParameters(parameters);
                    Log.d(TAG, "setPreviewSize() called with: width = " + previewSize.width + "; height = " + previewSize.height);
                    break;
                }
            }
        }
    }

    /**
     * 设置预览 Surface。
     */
    @WorkerThread
    private void setPreviewSurface(@Nullable SurfaceHolder previewSurface) {
        Camera camera = mCamera;
        if (camera != null && previewSurface != null) {
            try {
                camera.setPreviewDisplay(previewSurface);
                Log.d(TAG, "setPreviewSurface() called");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开始预览。
     */
    @WorkerThread
    private void startPreview() {
        Camera camera = mCamera;
        SurfaceHolder previewSurface = mPreviewSurface;
        if (camera != null && previewSurface != null) {
            camera.startPreview();
            Log.d(TAG, "startPreview() called");
        }
    }

    /**
     * 停止预览。
     */
    @WorkerThread
    private void stopPreview() {
        Camera camera = mCamera;
        if (camera != null) {
            camera.stopPreview();
            Log.d(TAG, "stopPreview() called");
        }
    }

    /**
     * 判断是否有后置摄像头。
     *
     * @return true 代表有后置摄像头
     */
    private boolean hasBackCamera() {
        return mBackCameraId != -1;
    }

    /**
     * 判断是否有前置摄像头。
     *
     * @return true 代表有前置摄像头
     */
    private boolean hasFrontCamera() {
        return mFrontCameraId != -1;
    }

    private void setupPreview(SurfaceHolder previewSurface, int surfaceWidth, int surfaceHeight) {
        Handler cameraHandler = mCameraHandler;
        if (cameraHandler != null) {
            cameraHandler.obtainMessage(MSG_SET_PREVIEW_SIZE, surfaceWidth, surfaceHeight).sendToTarget();
            cameraHandler.obtainMessage(MSG_SET_PREVIEW_SURFACE, previewSurface).sendToTarget();
            cameraHandler.sendEmptyMessage(MSG_START_PREVIEW);
        }
    }

    private class OnSwitchCameraButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Handler cameraHandler = mCameraHandler;
            SurfaceHolder previewSurface = mPreviewSurface;
            int previewSurfaceWidth = mPreviewSurfaceWidth;
            int previewSurfaceHeight = mPreviewSurfaceHeight;
            if (cameraHandler != null && previewSurface != null) {
                int cameraId = switchCameraId();// 切换摄像头 ID
                cameraHandler.sendEmptyMessage(MSG_STOP_PREVIEW);// 停止预览
                cameraHandler.sendEmptyMessage(MSG_CLOSE_CAMERA);// 关闭当前的摄像头
                cameraHandler.obtainMessage(MSG_OPEN_CAMERA, cameraId, 0).sendToTarget();// 开启新的摄像头
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SIZE, previewSurfaceWidth, previewSurfaceHeight).sendToTarget();// 配置预览尺寸
                cameraHandler.obtainMessage(MSG_SET_PREVIEW_SURFACE, previewSurface).sendToTarget();// 配置预览 Surface
                cameraHandler.sendEmptyMessage(MSG_START_PREVIEW);// 开启预览
            }
        }
    }

    private class PreviewSurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mPreviewSurface = holder;
            mPreviewSurfaceWidth = width;
            mPreviewSurfaceHeight = height;
            setupPreview(holder, width, height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mPreviewSurface = null;
            mPreviewSurfaceWidth = 0;
            mPreviewSurfaceHeight = 0;
        }
    }

}
