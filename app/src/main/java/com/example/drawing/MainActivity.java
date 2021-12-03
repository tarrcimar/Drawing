package com.example.drawing;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    DrawingView dv;
    ImageView image;
    private Paint mPaint;
    private Context context;

    //shake detection
    private AccelerometerEventListener acl = new AccelerometerEventListener();
    private Sensor accelerometer;
    private SensorManager mSensorMgr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        context = this;
        super.onCreate(savedInstanceState);
        dv = new DrawingView(this);
        mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            mSensorMgr.registerListener(acl, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            acl.setContext(context);
        }

        image = new ImageView(this);
        setContentView(dv);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.BLUE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(12);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorMgr.unregisterListener(acl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorMgr.registerListener(acl, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }


    public class DrawingView extends View {

        public int width;
        public int height;
        private Bitmap mBitmap;
        private Canvas mCanvas;
        private Path mPath;
        private Paint mBitmapPaint;
        Context context;
        private Paint circlePaint;
        private Path circlePath;

        public DrawingView(Context c) {
            super(c);
            context = c;
            mPath = new Path();
            mBitmapPaint = new Paint(Paint.DITHER_FLAG);
            circlePaint = new Paint();
            circlePath = new Path();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(Color.BLUE);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeJoin(Paint.Join.MITER);
            circlePaint.setStrokeWidth(4f);
            setDrawingCacheEnabled(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            width = w;
            height = h;

            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
            canvas.drawPath(mPath, mPaint);
            canvas.drawPath(circlePath, circlePaint);
        }

        private float mX, mY;
        private static final float TOUCH_TOLERANCE = 4;

        private void touch_start(float x, float y) {
            mPath.reset();
            mPath.moveTo(x, y);
            mX = x;
            mY = y;
        }

        private void touch_move(float x, float y) {
            float dx = Math.abs(x - mX);
            float dy = Math.abs(y - mY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                mX = x;
                mY = y;

                circlePath.reset();
                circlePath.addCircle(mX, mY, 30, Path.Direction.CW);
            }
        }

        private void touch_up() {
            mPath.lineTo(mX, mY);
            circlePath.reset();
            // commit the path to our offscreen
            mCanvas.drawPath(mPath, mPaint);
            // kill this so we don't double draw
            saveDrawing();
            mPath.reset();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touch_start(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    touch_move(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    touch_up();
                    invalidate();
                    break;
            }
            return true;
        }

        public void saveDrawing() {
            Bitmap userDrawing = getDrawingCache();
            userDrawing = ThumbnailUtils.extractThumbnail(userDrawing, width, height);
            image.setImageBitmap(userDrawing);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            setContentView(image);
            acl.setBitmapImage(image);
            acl.setHeight(height);
            acl.setWidth(width);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            userDrawing.compress(Bitmap.CompressFormat.PNG, 0, baos);
            byte[] byteArray;
            byteArray = baos.toByteArray();
        }
    }

    public class AccelerometerEventListener implements SensorEventListener {

        private static final float SHAKE_THRESHOLD = 0.8f;
        private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
        private long mLastShakeTime;

        private Context context;

        private ImageView imageView;
        private Bitmap bitmap;
        private int width;

        public void setWidth(int width) {
            this.width = width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        private int height;

        public void setBitmapImage(ImageView imageView) {
            this.imageView = imageView;
        }

        public void setContext(Context context) {
            this.context = context;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                long curTime = System.currentTimeMillis();
                if ((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLISECS) {

                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];

                    double acceleration = Math.sqrt(Math.pow(x, 2) +
                            Math.pow(y, 2) +
                            Math.pow(z, 2)) - SensorManager.GRAVITY_EARTH;
                    Log.d("DRAWING", "Acceleration is " + acceleration + "m/s^2");

                    if (acceleration > SHAKE_THRESHOLD) {
                        mLastShakeTime = curTime;
                        Log.d("DRAWING", "Shake, Rattle, and Roll");
                        Activity a = (MainActivity) context;
                        dv = new DrawingView(context);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        a.setContentView(dv);
                    }
                }
            }
        }

        /*
        public void modifyBitmap() throws InterruptedException {
            width = 256;
            height = 256;
            bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            int componentPerPixel = 3;
            int totalPixels = width * height;
            int totalBytes = totalPixels * componentPerPixel;

            byte[] rgbValues = new byte[totalBytes];
            @ColorInt int[] argbPixels = new int[totalPixels];
            mutableBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height);
            for(int i = 0; i < totalPixels; i++){
                @ColorInt int argbPixel = argbPixels[i];
                int red = Color.red(argbPixel);
                int green = Color.green(argbPixel);
                int blue = Color.blue(argbPixel);
                if(blue > 0){
                    mutableBitmap.setPixel(i%256, i/256, Color.argb(1, 255, 255, 255));
                    Thread.sleep(10);
                    Activity a = (MainActivity) context;
                    image = new ImageView(context);

                    image.setImageBitmap(mutableBitmap);
                    a.setContentView(dv);
                }

                rgbValues[i * componentPerPixel + 0] = (byte) red;
                rgbValues[i * componentPerPixel + 1] = (byte) green;
                rgbValues[i * componentPerPixel + 2] = (byte) blue;
                //Log.d("PIXEL RED", rgbValues[i * componentPerPixel + 0] + "");
                //Log.d("PIXEL GREEN", rgbValues[i * componentPerPixel + 1] + "");
                //Log.d("PIXEL BLUE", rgbValues[i * componentPerPixel + 2] + "");
            }
        }*/



        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }
}