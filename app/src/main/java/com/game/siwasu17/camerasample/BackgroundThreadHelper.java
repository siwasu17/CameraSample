package com.game.siwasu17.camerasample;

import android.os.HandlerThread;
import android.os.Handler;
import android.util.Log;

public class BackgroundThreadHelper {
    private static final String TAG = "BackgroundThreadHelper";
    private HandlerThread mThread = null;
    private Handler mHandler = null;

    public BackgroundThreadHelper(){
        return;
    }

    public void start(){
        mThread = new HandlerThread("CameraBackground");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    public Handler getmHandler(){
        if(mHandler == null){
            Log.e(TAG,"Background thread Error mHandler null");
        }
        return mHandler;
    }

    public void stop(){
        mThread.quitSafely();
        try{
            mThread.join();
            mThread = null;
            mHandler = null;
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }

}
