package com.game.siwasu17.camerasample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity implements CameraInterface {
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
        mCamera.setInterface(this);
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
                        new CompareSizesByArea());
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
                                mThread.getHandler().post(new ImageStore(imageReader.acquireNextImage(), file));

                            }
                        }, mThread.getHandler());
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
            manager.openCamera(cameraId, mCamera.stateCallback, mThread.getHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        mCamera.close();
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    //TextureListener
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            // SurfaceTextureの準備が完了した
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            // Viewのサイズに変更があったためPreviewサイズを計算し直す
            configurePreviewTransform(width, height);

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    private void setUpPreview(Size[] choices, int width, int height, Size aspectRatio) {
        // カメラ性能を超えたサイズを指定するとキャプチャデータにゴミがまじるため、注意

        // 表示するSurfaceより、高い解像度のプレビューサイズを抽出する
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // プレビューを表示するSurfaceに最も近い（小さな）解像度を選択する
        if (bigEnough.size() > 0) {
            mPreviewSize = Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            mPreviewSize = choices[0];
        }

        // プレビューが歪まないようにアスペクト比を調整する
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.setAspectRatio(
                    mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
            mTextureView.setAspectRatio(
                    mPreviewSize.getHeight(), mPreviewSize.getHeight());
        }
    }

    private void configurePreviewTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    // Parmission handling for Android 6.0
    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // 権限チェックした結果、持っていない場合はダイアログを出す
            new AlertDialog.Builder(this)
                    .setMessage("Request Permission")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CODE_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    })
                    .create();
            return;
        }

        //権限を取得する
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        return;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setMessage("Need Camera Permission")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        })
                        .create();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public SurfaceTexture getSurfaceTextureFromTextureView() {
        return mTextureView.getSurfaceTexture();
    }

    @Override
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    public Handler getBackgroundHandler() {
        return mThread.getHandler();
    }

    @Override
    public Surface getImageRenderSurface() {
        return mImageReader.getSurface();
    }

    @Override
    public int getRotation() {
        return getWindowManager().getDefaultDisplay().getRotation();
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
