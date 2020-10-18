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
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
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
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class H264VideoView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "H264VideoView";
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;

    private MediaCodec mMediaCodec;
    private Lock mReadyLock;

    private int counter = 0;

    //private MySurface mySurface;
    private boolean mIsCodecConfigured = false;

    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;

    private ByteBuffer[] mBuffers;

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 368;

    private SurfaceHolder mSurfaceHolder;

    private ObjectDetector mObjectDetector;
    private FaceDetector mFaceDetector;

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

        FaceDetectorOptions realTimeOpts =
                new FaceDetectorOptions.Builder()
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        //.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        //.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        //.enableTracking()
                        .build();

        mFaceDetector = FaceDetection.getClient(realTimeOpts);


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
        counter++;

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
                if(outIndex>0 ){

                    final Image image = mMediaCodec.getOutputImage(outIndex);
                    long timeBegin = System.nanoTime();

                    //final Bitmap bitmap = getBitmap(image );
                    ByteBuffer nv21Buffer =
                            yuv420ThreePlanesToNV21(image.getPlanes(), 640, 368);
                    Log.i("DATE3", Long.toString(System.nanoTime()-timeBegin));

                    //InputImage inputImage = InputImage.fromBitmap(bitmap,0);
                    //MOET NV21 zijn of het gaat veel te traag!!!!!!!!!!!!!!!
                    InputImage inputImage = InputImage.fromByteBuffer(nv21Buffer, VIDEO_WIDTH, VIDEO_HEIGHT, 0, ImageFormat.NV21);
                    Log.i("COUNTER", Integer.toString(counter));


                        //FACEDETECTION TO SLOW

                        Task<List<Face>> result =
                                mFaceDetector.process(inputImage)
                                        .addOnSuccessListener(
                                                new OnSuccessListener<List<Face>>() {
                                                    @Override
                                                    public void onSuccess(List<Face> faces) {
                                                        Log.i("NO FACE DETECTED", "no face");
                                                        if (faces.size()==0){
                                                            mImageReadyCallback.drawImage(null,null);
                                                        }
                                                        for(Face face : faces) {
                                                            Log.i("FACE", face.getBoundingBox().toShortString());
                                                            mImageReadyCallback.drawImage(null,face.getBoundingBox() );
                                                        }

                                                    }
                                                })
                                        .addOnFailureListener(
                                                new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        // Task failed with an exception
                                                        // ...
                                                        Log.e("ERROR", e.getMessage()+" "+ Integer.toString( ((MlKitException)e).getErrorCode()));
                                                    }
                                                });



//                    mImageReadyCallback.drawImage(bitmap,null );
               /* mObjectDetector.process(inputImage)
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

*/
                    image.close();

                }



                while (outIndex >= 0) {
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);
                }


            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }




        mReadyLock.unlock();


    }

    public Bitmap getBitmap() {
        setDrawingCacheEnabled(true);
        buildDrawingCache(true);
        Bitmap bitmap = Bitmap.createBitmap( getDrawingCache() );
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


    public static Bitmap getBitmap(ByteBuffer data, int [] strides) {


        // Convert YUV to RGB
/*
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



 */





        Long time = System.nanoTime();
        data.rewind();
        byte[] imageInBuffer = new byte[data.limit()];
        data.get(imageInBuffer, 0, imageInBuffer.length);
        try {

            YuvImage image =
                    new YuvImage(
                            imageInBuffer, ImageFormat.NV21, VIDEO_WIDTH, VIDEO_HEIGHT, null);//testStride);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT), 100, stream);

            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

            stream.close();
            Log.i("DATE2", Long.toString(System.nanoTime()- time));
            return bmp;
        } catch (Exception e) {
            Log.e("VisionProcessorBase", "Error: " + e.getMessage());
        }
        return null;
    }


    /// FROM BITMAPUTILS
    public static Bitmap getBitmap(final Image image) {
        Long time  =System.nanoTime();

        ByteBuffer nv21Buffer =
                yuv420ThreePlanesToNV21(image.getPlanes(), 640, 368);
        Log.i("DATE", Long.toString(System.nanoTime()-time));
        Bitmap bitmap = getBitmap(nv21Buffer, null);
        Log.i("DATE bitmap", Long.toString(System.nanoTime()-time));
        return bitmap;

    }

    private static ByteBuffer yuv420ThreePlanesToNV21(
            Image.Plane[] yuv420888planes, int width, int height) {
        int imageSize = width * height;
        byte[] out = new byte[imageSize + 2 * (imageSize / 4)];

/*
        if (areUVPlanesNV21(yuv420888planes, width, height)) {
            Log.i("SCEN1", "SCEN1");
            // Copy the Y values.
            yuv420888planes[0].getBuffer().get(out, 0, imageSize);

            ByteBuffer uBuffer = yuv420888planes[1].getBuffer();
            ByteBuffer vBuffer = yuv420888planes[2].getBuffer();
            // Get the first V value from the V buffer, since the U buffer does not contain it.
            vBuffer.get(out, imageSize, 1);
            // Copy the first U value and the remaining VU values from the U buffer.
            uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1);
        } else {*/
            Log.i("SCEN2", "SCEN2");
            // Fallback to copying the UV values one by one, which is slower but also works.
            // Unpack Y.
            unpackPlane(yuv420888planes[0], width, height, out, 0, 1);
            // Unpack U.
            unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2);
            // Unpack V.
            unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2);
       // }

        return ByteBuffer.wrap(out);
    }

    private static boolean areUVPlanesNV21(Image.Plane[] planes, int width, int height) {
        int imageSize = width * height;

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // Backup buffer properties.
        int vBufferPosition = vBuffer.position();
        int uBufferLimit = uBuffer.limit();

        // Advance the V buffer by 1 byte, since the U buffer will not contain the first V value.
        vBuffer.position(vBufferPosition + 1);
        // Chop off the last byte of the U buffer, since the V buffer will not contain the last U value.
        uBuffer.limit(uBufferLimit - 1);

        // Check that the buffers are equal and have the expected number of elements.
        boolean areNV21 =
                (vBuffer.remaining() == (2 * imageSize / 4 - 2)) && (vBuffer.compareTo(uBuffer) == 0);

        // Restore buffers to their initial state.
        vBuffer.position(vBufferPosition);
        uBuffer.limit(uBufferLimit);

        return areNV21;
    }

    private static void unpackPlane(
            final Image.Plane plane, int width, int height, byte[] out, int offset, int pixelStride) {
        Long time  =System.nanoTime();
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();

        // Compute the size of the current plane.
        // We assume that it has the aspect ratio as the original image.
        int numRow = (buffer.limit() + plane.getRowStride() - 1) / plane.getRowStride();
        if (numRow == 0) {
            return;
        }
        int scaleFactor = height / numRow;
        int numCol = width / scaleFactor;

        // Extract the data in the output buffer.
        int outputPos = offset;
        int rowStart = 0;
        for (int row = 0; row < numRow; row++) {
            int inputPos = rowStart;
            for (int col = 0; col < numCol; col++) {
                out[outputPos] = buffer.get(inputPos);
                outputPos += pixelStride;
                inputPos += plane.getPixelStride();
            }
            rowStart += plane.getRowStride();
        }

        Log.i("DATE4", Long.toString(System.nanoTime()- time));
    }
}
