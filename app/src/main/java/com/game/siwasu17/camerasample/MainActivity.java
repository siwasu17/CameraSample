package com.game.siwasu17.camerasample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.VideoProfile;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends AppCompatActivity implements CameraInterface {
    private static final String TAG = "Camera2App";
    private int REQUEST_CODE_CAMERA_PERMISSION = 0x01;

    private Size mPreviewSize;
    private AutoFitTextureView mTextureView;

    private ImageReader mImageReader;
    private BackgroundThreadHelper mThread;
    private BasicCamera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);
        mThread = new BackgroundThreadHelper();
        mCamera = new BasicCamera();
        findViewById(R.id.picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCamera.takePicture();
            }
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        mThread.start();

        if (mTextureView.isAvailable()) {
            // Preview用のTextureViewの準備ができている
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            // 準備完了通知を受け取るためにリスナーを登録する
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        mThread.stop();
        super.onPause();
    }

    private String setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                // フロントカメラを利用しない
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                // ストリーム制御をサポートしていない場合、セットアップを中断する
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // 最大サイズでキャプチャする
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizeByArea());
                setUpPreview(map.getOutputSizes(SurfaceTexture.class),
                        width, height, largest);
                configurePreviewTransform(width, height);

                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(
                        new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader imageReader) {
                                File file = new File(getExternalFilesDir(null), "pic.jpeg");
                                mThread.getmHandler().post(new ImageStore(imageReader.acquireNextImage(), file));

                            }
                        }, mThread.getmHandler());
                return cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Camera2 API未サポート
            Log.e(TAG, "Camera Error:not support Camera2API");
        }

        return null;
    }

    private void openCamera(int width, int height) {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        String cameraId = setUpCameraOutputs(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCamera.isLocked()) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, mCamera.stateCallback, mThread.getmHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera(){
        mCamera.close();
        if(null != mImageReader){
            mImageReader.close();
            mImageReader = null;
        }
    }


    @Override
    public SurfaceTexture getSurfaceTextureFromTextureView() {
        return null;
    }

    @Override
    public Size getPreviewSize() {
        return null;
    }

    @Override
    public Handler getBackgroundHandler() {
        return null;
    }

    @Override
    public Surface getImageRenderSurface() {
        return null;
    }

    @Override
    public int getRotation() {
        return 0;
    }
}
