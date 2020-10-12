package com.wasp2.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptGroup;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.wasp2.MainActivity;
import com.wasp2.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class H264VideoView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "H264VideoView";
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;

    private MediaCodec mMediaCodec;
    private Lock mReadyLock;

    private MySurface mySurface;
    private boolean mIsCodecConfigured = false;

    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;

    private ByteBuffer[] mBuffers;

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 368;

    private SurfaceHolder mSurfaceHolder;

    private ObjectDetector mObjectDetector;

    private ImageReadyCallback mImageReadyCallback;

    public H264VideoView(Context context) {
        super(context);
        Log.i("SURFACE", "SURFACE");

        customInit();
    }

    public H264VideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        customInit();
    }

    public H264VideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        customInit();
    }

    private void customInit() {
        mReadyLock = new ReentrantLock();
        getHolder().addCallback(this);

        //OBJECT DETECTION////

        // Live detection and tracking
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableMultipleObjects()
                        .enableClassification()  // Optional
                        .build();


        mObjectDetector = ObjectDetection.getClient(options);

        /////////////////////
    }


    public void displayFrame(ARFrame frame) {


        mReadyLock.lock();

        if ((mMediaCodec != null)) {
            if (mIsCodecConfigured) {
                // Here we have either a good PFrame, or an IFrame
                int index = -1;

                try {
                    index = mMediaCodec.dequeueInputBuffer(VIDEO_DEQUEUE_TIMEOUT);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error while dequeue input buffer");
                }
                if (index >= 0) {
                    ByteBuffer b;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        b = mMediaCodec.getInputBuffer(index);
                    } else {
                        b = mBuffers[index];
                        b.clear();
                    }

                    if (b != null) {
                        b.put(frame.getByteData(), 0, frame.getDataSize());
                        //Log.i("BYTEDATA", Integer.toString(frame.getDataSize()));
                        //bytes are written

                    }

                    try {
                        mMediaCodec.queueInputBuffer(index, 0, frame.getDataSize(), 0, 0);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            // Try to display previous frame
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex;
            try {
                outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);
                Log.i("outIndex",Integer.toString(outIndex));
                Image image = mMediaCodec.getOutputImage(outIndex);
                //Log.i("FORMAT",Integer.toString(image.getFormat())); //YUV_420_888


                final ByteBuffer yuvBytes = this.imageToByteBuffer(image);

                // Convert YUV to RGB

                final RenderScript rs = RenderScript.create(this.getContext());

                final Bitmap        bitmap     = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                final Allocation allocationRgb = Allocation.createFromBitmap(rs, bitmap);

                final Allocation allocationYuv = Allocation.createSized(rs, Element.U8(rs), yuvBytes.array().length);
                allocationYuv.copyFrom(yuvBytes.array());

                ScriptIntrinsicYuvToRGB scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
                scriptYuvToRgb.setInput(allocationYuv);
                scriptYuvToRgb.forEach(allocationRgb);

                allocationRgb.copyTo(bitmap);
                Log.i("DRAWING", "DRAWING");



                // Release



                allocationYuv.destroy();
                allocationRgb.destroy();
                rs.destroy();




                //



               InputImage inputImage = InputImage.fromBitmap(bitmap,0);
                //InputImage inputImage = InputImage.fromMediaImage(image,0);


                mObjectDetector.process(inputImage)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<DetectedObject>>() {
                                    @Override
                                    public void onSuccess(List<DetectedObject> detectedObjects) {
                                        Log.i("OBJECTS DETECTEDD", Integer.toString(detectedObjects.size()));
                                        for(DetectedObject object : detectedObjects) {
                                            Log.i("BOUNDINGBOX", Integer.toString(object.getBoundingBox().bottom));
                                            Log.i("LABELS", Integer.toString(object.getLabels().size()));
                                            for (DetectedObject.Label label : object.getLabels()) {
                                                Log.i("object", label.getText());
                                            }
                                            mImageReadyCallback.drawImage(bitmap,object.getBoundingBox() );

                                        }
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.i("ERROR",Integer.toString( ((MlKitException)e).getErrorCode()));
                                    }
                                });




                while (outIndex >= 0) {
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);
                }




                image.close();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }




        mReadyLock.unlock();


    }

    public Bitmap getBitmap() {
        setDrawingCacheEnabled(true);
        buildDrawingCache(true);
        final Bitmap bitmap = Bitmap.createBitmap( getDrawingCache() );
        setDrawingCacheEnabled(false);
        destroyDrawingCache();
        return bitmap;
    }

    public void configureDecoder(ARControllerCodec codec) {
        mReadyLock.lock();

        if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
            ARControllerCodec.H264 codecH264 = codec.getAsH264();

            mSpsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
            mPpsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
        }

        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }

        mReadyLock.unlock();
    }

    private void configureMediaCodec() {
        mMediaCodec.stop();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", mSpsBuffer);
        format.setByteBuffer("csd-1", mPpsBuffer);


        //sturen naar myholder, en die moet het doorsturen!
        Log.i("CREATING SURFACE", "CREATING SURFACE");
       // mySurface= new MySurface(new SurfaceTexture(true));
       // mMediaCodec.configure(format, mySurface, null, 0);


        //mMediaCodec.configure(format, getHolder().getSurface(), null, 0);
        mMediaCodec.configure(format, null, null, 0);
        mMediaCodec.start();

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            mBuffers = mMediaCodec.getInputBuffers();
        }

        mIsCodecConfigured = true;
    }

    private void initMediaCodec(String type) {
        try {
            mMediaCodec = MediaCodec.createDecoderByType(type);
        } catch (IOException e) {
            Log.e(TAG, "Exception", e);
        }

        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }
    }

    private void releaseMediaCodec() {
        if (mMediaCodec != null) {
            if (mIsCodecConfigured) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
            mIsCodecConfigured = false;
            mMediaCodec = null;
        }
    }


    //SURFACE OVERRIDE METHODS

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        mReadyLock.lock();
        initMediaCodec(VIDEO_MIME_TYPE);

        mReadyLock.unlock();


    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //Log.i("SURFACE CHANGED", "SURFACE CHANGED");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mReadyLock.lock();
        releaseMediaCodec();
        mReadyLock.unlock();
    }


    public void setImageDisplayCallBack(ImageReadyCallback callback) {
        this.mImageReadyCallback = callback;
    }

    private ByteBuffer imageToByteBuffer(final Image image)
    {
        final Rect crop   = image.getCropRect();
        final int  width  = crop.width();
        final int  height = crop.height();

        final Image.Plane[] planes     = image.getPlanes();
        final byte[]        rowData    = new byte[planes[0].getRowStride()];
        final int           bufferSize = width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        final ByteBuffer    output     = ByteBuffer.allocateDirect(bufferSize);

        int channelOffset = 0;
        int outputStride = 0;

        for (int planeIndex = 0; planeIndex < 3; planeIndex++)
        {
            if (planeIndex == 0)
            {
                channelOffset = 0;
                outputStride = 1;
            }
            else if (planeIndex == 1)
            {
                channelOffset = width * height + 1;
                outputStride = 2;
            }
            else if (planeIndex == 2)
            {
                channelOffset = width * height;
                outputStride = 2;
            }

            final ByteBuffer buffer      = planes[planeIndex].getBuffer();
            final int        rowStride   = planes[planeIndex].getRowStride();
            final int        pixelStride = planes[planeIndex].getPixelStride();

            final int shift         = (planeIndex == 0) ? 0 : 1;
            final int widthShifted  = width >> shift;
            final int heightShifted = height >> shift;

            //buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            buffer.position(rowStride * (crop.top ) + pixelStride * (crop.left >> shift ));

            for (int row = 0; row < heightShifted; row++)
            {
                final int length;

                if (pixelStride == 1 && outputStride == 1)
                {
                    length = widthShifted;
                    buffer.get(output.array(), channelOffset, length);
                    channelOffset += length;
                }
                else
                {
                    length = (widthShifted - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);

                    for (int col = 0; col < widthShifted; col++)
                    {
                        output.array()[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }

                if (row < heightShifted - 1)
                {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }

        return output;
    }
}
