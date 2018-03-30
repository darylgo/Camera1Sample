package com.darylgo.camera.sample;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Nullable
    private Camera.CameraInfo mFrontCameraInfo = null;
    private int mFrontCameraId = -1;

    @Nullable
    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;

    @Nullable
    private Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initCameraInfo();
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
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                // 前置摄像头信息
                mFrontCameraId = cameraId;
                mFrontCameraInfo = cameraInfo;
            }
        }
    }

    /**
     * 开启指定摄像头
     */
    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("相机已经被开启，无法同时开启多个相机实例！");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (hasFrontCamera()) {
                // 优先开启前置摄像头
                mCamera = Camera.open(mFrontCameraId);
            } else if (hasBackCamera()) {
                // 没有前置，就尝试开启后置摄像头
                mCamera = Camera.open(mBackCameraId);
            } else {
                throw new RuntimeException("没有任何相机可以开启！");
            }
        }
    }

    /**
     * 关闭相机。
     */
    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
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

    @Override
    protected void onStart() {
        super.onStart();
        // 动态权限检查
        if (!isRequiredPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    onRequiredPermissionsDenied();
                    return;
                }
            }
            onRequiredPermissionsGranted();
        }
    }

    /**
     * 当任何权限被用户拒绝的时候触发该方法，我们弹窗通知用户权限被禁用，只能关闭程序。
     */
    private void onRequiredPermissionsDenied() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("权限检查");
        dialogBuilder.setMessage("检查到相关权限被拒绝，程序无法正常运行！");
        dialogBuilder.setNeutralButton("关闭程序", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        dialogBuilder.show();
    }

    /**
     * 当所有权限都被授予的时候回调该方法。
     */
    private void onRequiredPermissionsGranted() {
        openCamera();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Window window = getWindow();
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

}
