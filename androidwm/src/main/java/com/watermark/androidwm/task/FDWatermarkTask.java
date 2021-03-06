/*
 *    Copyright 2018 Yizheng Huang
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.watermark.androidwm.task;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;

import com.watermark.androidwm.listener.BuildFinishListener;
import com.watermark.androidwm.bean.AsyncTaskParams;
import com.watermark.androidwm.utils.BitmapUtils;
import com.watermark.androidwm.utils.FastDctFft;

import static com.watermark.androidwm.utils.BitmapUtils.pixel2ARGBArray;
import static com.watermark.androidwm.utils.BitmapUtils.getBitmapPixels;
import static com.watermark.androidwm.utils.Constant.CHUNK_SIZE;
import static com.watermark.androidwm.utils.Constant.ERROR_CREATE_FAILED;
import static com.watermark.androidwm.utils.Constant.ERROR_NO_WATERMARKS;
import static com.watermark.androidwm.utils.Constant.ERROR_PIXELS_NOT_ENOUGH;
import static com.watermark.androidwm.utils.Constant.FD_IMG_PREFIX_FLAG;
import static com.watermark.androidwm.utils.Constant.FD_IMG_SUFFIX_FLAG;
import static com.watermark.androidwm.utils.Constant.FD_TEXT_PREFIX_FLAG;
import static com.watermark.androidwm.utils.Constant.FD_TEXT_SUFFIX_FLAG;
import static com.watermark.androidwm.utils.StringUtils.copyFromIntArray;
import static com.watermark.androidwm.utils.StringUtils.stringToBinary;
import static com.watermark.androidwm.utils.StringUtils.stringToIntArray;

/**
 * This is a tack that use Fast Fourier Transform for an image, to
 * build the image and text watermark into a frequency domain.
 *
 * @author huangyz0918 (huangyz0918@gmail.com)
 */
public class FDWatermarkTask extends AsyncTask<AsyncTaskParams, Void, Bitmap> {

    private BuildFinishListener<Bitmap> listener;

    public FDWatermarkTask(BuildFinishListener<Bitmap> callback) {
        this.listener = callback;
    }

    @Override
    protected Bitmap doInBackground(AsyncTaskParams... params) {
        Bitmap backgroundBitmap = params[0].getBackgroundImg();
        String watermarkString = params[0].getWatermarkText();
        Bitmap watermarkBitmap = params[0].getWatermarkImg();

        // checkout if the kind of input watermark is bitmap or a string text.
        // add convert them into an ascii string.
        if (watermarkBitmap != null) {
            watermarkString = BitmapUtils.bitmapToString(watermarkBitmap);
        }

        if (watermarkString == null) {
            listener.onFailure(ERROR_NO_WATERMARKS);
            return null;
        }

        String watermarkBinary = stringToBinary(watermarkString);
        if (watermarkBitmap != null) {
            watermarkBinary = FD_IMG_PREFIX_FLAG + watermarkBinary + FD_IMG_SUFFIX_FLAG;
        } else {
            watermarkBinary = FD_TEXT_PREFIX_FLAG + watermarkBinary + FD_TEXT_SUFFIX_FLAG;
        }

        int[] watermarkColorArray = stringToIntArray(watermarkBinary);

        Bitmap outputBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(),
                backgroundBitmap.getConfig());

        // convert the background bitmap into pixel array.
        int[] backgroundPixels = getBitmapPixels(backgroundBitmap);

        if (watermarkColorArray.length > backgroundPixels.length * 4) {
            listener.onFailure(ERROR_PIXELS_NOT_ENOUGH);
        } else {
            // divide and conquer
            if (backgroundPixels.length < CHUNK_SIZE) {
                int[] backgroundColorArray = pixel2ARGBArray(backgroundPixels);
                double[] backgroundColorArrayD = copyFromIntArray(backgroundColorArray);

                FastDctFft.transform(backgroundColorArrayD);

                // do the operations.

                FastDctFft.inverseTransform(backgroundColorArrayD);
                double scale = (double) backgroundColorArrayD.length / 2;
                for (int j = 0; j < backgroundColorArrayD.length; j++) {
                    backgroundColorArrayD[j] = (int) Math.round(backgroundColorArrayD[j] / scale);
                }

                for (int i = 0; i < backgroundPixels.length; i++) {
                    int color = Color.argb(
                            (int) backgroundColorArrayD[4 * i],
                            (int) backgroundColorArrayD[4 * i + 1],
                            (int) backgroundColorArrayD[4 * i + 2],
                            (int) backgroundColorArrayD[4 * i + 3]
                    );

                    backgroundPixels[i] = color;
                }
            } else {
                int numOfChunks = (int) Math.ceil((double) backgroundPixels.length / CHUNK_SIZE);
                for (int i = 0; i < numOfChunks; i++) {
                    int start = i * CHUNK_SIZE;
                    int length = Math.min(backgroundPixels.length - start, CHUNK_SIZE);
                    int[] temp = new int[length];
                    System.arraycopy(backgroundPixels, start, temp, 0, length);
                    double[] colorTempD = copyFromIntArray(pixel2ARGBArray(temp));
                    FastDctFft.transform(colorTempD);

                    // do the operations.
                    FastDctFft.inverseTransform(colorTempD);

                    double scale = (double) colorTempD.length / 2;
                    for (int j = 0; j < colorTempD.length; j++) {
                        colorTempD[j] = (int) Math.round(colorTempD[j] / scale);
                    }

                    for (int j = 0; j < length; j++) {
                        int color = Color.argb(
                                (int) colorTempD[4 * j],
                                (int) colorTempD[4 * j + 1],
                                (int) colorTempD[4 * j + 2],
                                (int) colorTempD[4 * j + 3]
                        );

                        backgroundPixels[start + j] = color;
                    }
                }
            }

            outputBitmap.setPixels(backgroundPixels, 0, backgroundBitmap.getWidth(), 0, 0,
                    backgroundBitmap.getWidth(), backgroundBitmap.getHeight());
            return outputBitmap;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (listener != null) {
            if (bitmap != null) {
                listener.onSuccess(bitmap);
            } else {
                listener.onFailure(ERROR_CREATE_FAILED);
            }
        }
        super.onPostExecute(bitmap);
    }

}