package com.game.siwasu17.camerasample;


import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageStore implements Runnable {

    private final Image mImage;
    private final File mFile;

    public ImageStore(Image mImage, File mFile) {
        this.mImage = mImage;
        this.mFile = mFile;
    }

    @Override
    public void run() {
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            output.write(bytes);
        }catch(IOException e){
            e.printStackTrace();
        }finally {
            mImage.close();
            if(null != output){
                try{
                    output.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }

    }
}
