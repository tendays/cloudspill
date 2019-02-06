/*
 * TouchImageView.java
 * By: Michael Ortiz
 * Updated By: Patrick Lackemacher
 * Updated By: Babay88
 * Updated By: @ipsilondev
 * Updated By: hank-cp
 * Updated By: singpolyma
 * -------------------
 * Extends Android ImageView to include pinch zooming, panning, fling and double tap zoom.
 */

package com.ortiz.touch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;

import org.gamboni.cloudspill.shared.util.ImageOrientationUtil;

import java.io.IOException;

public class TouchImageView extends AppCompatImageView {
    private static final String TAG = "CloudSpill.ImageView";

    /** Immutable variant of Point */
    private static class Coordinate implements Parcelable {
        public static final Creator<Coordinate> CREATOR = new Creator<Coordinate>() {
            @Override
            public Coordinate createFromParcel(Parcel source) {
                return new Coordinate(source.readFloat(), source.readFloat());
            }

            @Override
            public Coordinate[] newArray(int size) {
                return new Coordinate[size];
            }
        };

        final float x, y;
        Coordinate(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeFloat(x);
            dest.writeFloat(y);
        }

        public String toString() {
            return "Coordinate("+ x +", "+ y +")";
        }
    }

    private static class Dimensions {
        final int width,height;

        Dimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /** Sum of width and height */
        int resolution() {
            return width + height;
        }

        float relativeWidth() {
            return (float) width / resolution();
        }

        float relativeHeight() {
            return (float) height / resolution();
        }
        Coordinate relativeCenter() {
            return new Coordinate(
                            (float)relativeWidth() / 2,
                            (float)relativeHeight() / 2);
        }

        Dimensions flipped() {
            return new Dimensions(height, width);
        }
    }

    private static class State implements Parcelable {

        /** Which part of the image is shown at the center of the screen, in relative image coordinates. "Relative image coordinates"
         * set the top-left corner to (0, 0), and are such that the image has perimeter equal to 2.
         *
         * Design notes: we use the centre as reference, as it's invariant wrt rotation. We use coordinates relative to image size
         * to be invariant with image resolution change. We could have set the bottom-right corner to be at (1, 1) but that would not preserve
         * angle and relative sizes if the image is not square. {@link #touchDistance} would also be ill-defined, for the same reason. */
        final Coordinate focusPoint;
        /** Zoom level: the ratio of apparent (rendered) image half-perimeter to screen half-perimeter (half-perimeter = width + height)
         *
         * Design notes: apparent size of an image feature should be invariant with
         * image resolution changes (e.g. when switch from low-resolution image to high-resolution image (in response to
         * user zooming or when switch from a low-resolution cached image to a downloaded high-resolution image), the apparent size should not change.
         * Apparent size of an image feature should not change when screen width and height are swapped (i.e. when user rotates device).
         *
         * (Note the image may *still* appear to grow in size when rotating the screen in response to device
         * orientation change in order to fill bounds - but that is implemented by increasing the zoom level) */
        final float zoom;
        /** Rotation, counter-clockwise, in degrees, applied to the image before it is shown on screen. */
        final int rotation;

        public static final Creator<State> CREATOR = new Creator<State>() {
            @Override
            public State createFromParcel(Parcel source) {
                return new State(source.<Coordinate>readParcelable(State.class.getClassLoader()),
                        source.readFloat(),
                        source.readInt());
            }

            @Override
            public State[] newArray(int size) {
                return new State[size];
            }
        };

        State(Coordinate focusPoint, float zoom, int rotation) {
            this.focusPoint = focusPoint;
            this.zoom = zoom;
            this.rotation = rotation;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(focusPoint, 0);
            dest.writeFloat(zoom);
            dest.writeInt(rotation);
        }

        State withRotation(int rotation) {
            return new State(focusPoint, zoom, rotation);
        }
    }

    Dimensions viewDimensions;
    State state = new State(new Coordinate(0, 0), 1, 0);

    // Handling of touch state
    /** Which part of the image is being touched right now, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
    Coordinate touchPosition;

    /** The distance between two fingers, in case of two-finger touch, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
    float touchDistance;

    private final Context context;
    private boolean onDrawReady = false;

    /** Image Uri to load as soon as we know our size. */
    private Uri uriToLoad = null;

    public TouchImageView(Context context) {
        super(context);
        this.context = context;
        sharedConstructing();
    }

    public TouchImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        sharedConstructing();
    }
    
    public TouchImageView(Context context, AttributeSet attrs, int defStyle) {
    	super(context, attrs, defStyle);
    	this.context = context;
    	sharedConstructing();
    }
    
    private void sharedConstructing() {
        super.setClickable(true);
    }

    @Override
    public void setImageResource(int resId) {
    	super.setImageResource(resId);
    	fitImageToView();
    }
    
    @Override
    public void setImageBitmap(Bitmap bm) {
    	super.setImageBitmap(bm);
    	fitImageToView();
    }
    
    @Override
    public void setImageDrawable(Drawable drawable) {
    	super.setImageDrawable(drawable);
    	fitImageToView();
    }
    
    @Override
    public void setImageURI(Uri uri) {
        setScaledImage(uri);

    	fitImageToView();
    }
    
    @Override
    public Parcelable onSaveInstanceState() {
    	Bundle bundle = new Bundle();
    	bundle.putParcelable("instanceState", super.onSaveInstanceState());
    	bundle.putParcelable("state", state);
    	return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
      	if (state instanceof Bundle) {
	        Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
            this.state = bundle.getParcelable("state");
	        return;
      	}

      	super.onRestoreInstanceState(state);
    }

    @Override
    protected void onDraw(Canvas canvas) {
    	onDrawReady = true;
        if (uriToLoad != null) {
    	    Uri localUri = this.uriToLoad;
    	    this.uriToLoad = null;
    	    setScaledImage(localUri);
        }
        debug("Drawing with matrix", getImageMatrix());
    	super.onDraw(canvas);
    }

    /* Source: https://stackoverflow.com/questions/10200256/out-of-memory-error-imageview-issue */
    private void setScaledImage(final Uri uri) {
        final int imageViewHeight = getHeight();
        final int imageViewWidth = getWidth();
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            // Waiting to know our size before loading the image
            this.uriToLoad = uri;
        } else {
            // TODO use a persistent thread rather than creating one each time
            new Thread() {
                public void run() {
                    try {

                        final Bitmap bitmap = decodeSampledBitmapFromUri(uri, imageViewWidth, imageViewHeight);

                        state = state.withRotation(getRotation(ImageOrientationUtil.getExifRotation(context.getContentResolver().openInputStream(uri))));

                        Handler handler = TouchImageView.this.getHandler();
                        if (handler != null) {
                            handler.post(new Runnable() {
                                public void run() {
                                    setImageBitmap(bitmap);
                                }
                            });
                        } // else: view destroyed before image could be loaded
                    } catch (IOException e) {
                        Log.e("TouchImageView", "Failed loading image data", e);
                    }
                }
            }.start();
        }
    }

    private volatile boolean lowMem;

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) throws IOException {
        try {
            // First decode with inJustDecodeBounds = true to check dimensions
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri), null, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight, lowMem);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri), null, options);
        } catch (OutOfMemoryError oom) {
            if (lowMem) {
                throw oom;
            } else {
                Log.w("TouchImageView", "Caught OOM; switching to Low Memory Mode");
                lowMem = true;
                return decodeSampledBitmapFromUri(uri, reqWidth, reqHeight);
            }
        }
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight, boolean lowMem) {

        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        if (lowMem) {
            inSampleSize *= 2;
        }

        Log.i("TouchImageView", "Required size: "+ reqWidth +"×"+ reqHeight +". LowMem="+ lowMem +", Image size: "+ width +"×"+ height +
                ". Calculated sample size: "+ inSampleSize);

        return inSampleSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable drawable = getDrawable();
        int drawableWidth, drawableHeight;

        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            drawableWidth = 1;
            drawableHeight = 1;
        } else {
            drawableWidth = drawable.getIntrinsicWidth();
            drawableHeight = drawable.getIntrinsicHeight();
        }
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        this.viewDimensions = new Dimensions(setViewSize(widthMode, widthSize, drawableWidth),
            setViewSize(heightMode, heightSize, drawableHeight));
        
        /* Set view dimensions */
        setMeasuredDimension(viewDimensions.width, viewDimensions.height);
        
        /* Fit content within view */
        fitImageToView();
    }

    private boolean widthHeightFlipped(int rotation) {
        return (rotation == 90 || rotation == 270);
    }

    private int getRotation(int orientation) {
        if (orientation == 6) {
            return 90;
        } else if (orientation == 3) {
            return 180;
        } else if (orientation == 8) {
            return 270;
        } else {
            return 0;
        }
    }

    private Dimensions drawableDimensions() {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return new Dimensions(1, 1);
        }
        return new Dimensions(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }
    
    private void fitImageToView() {

        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
        	return;
        }
        
        Dimensions drawableDimensions = drawableDimensions();
        if (widthHeightFlipped(state.rotation)) {
            drawableDimensions = drawableDimensions.flipped();
        }

        /* Compute zoom to have entire image visible */
        float zoom = Math.min(viewDimensions.relativeWidth() / drawableDimensions.relativeWidth(),
            viewDimensions.relativeHeight() / drawableDimensions.relativeHeight());

        this.state = new State(
                /* Centre */
                drawableDimensions.relativeCenter(),
                zoom,
                this.state.rotation);

        Matrix m = new Matrix();
        // scale drawable resolution to 1.0
        m.postScale(1.0f / drawableDimensions.resolution(), 1.0f / drawableDimensions.resolution());
        // move the focus point to the origin
        m.postTranslate(- state.focusPoint.x, - state.focusPoint.y);
        // rotate image
        m.postRotate(state.rotation);
        // scale to final size
        m.postScale(state.zoom * viewDimensions.resolution(), state.zoom * viewDimensions.resolution());
        // move focus point to view center
        m.postTranslate(viewDimensions.width / 2, viewDimensions.height / 2);

        debug("fitImageToView setting matrix ", m);
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(m);
    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        debug("setImageMatrix called", matrix);
        super.setImageMatrix(matrix);
    }

    private void debug(String text, Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);

        Log.d(TAG, text + "[("+ values[0] +" "+ values[1] +" "+ values[2] +") ("+ values[3] +" "+ values[4] +" "+ values[5] +") ("+ values[6] +" "+ values[7] +" "+ values[8] +")]");
    }

    /**
     * Set view dimensions based on layout params
     *
     * @param mode
     * @param size
     * @param drawableSize
     * @return
     */
    private int setViewSize(int mode, int size, int drawableSize) {
    	switch (mode) {
		case MeasureSpec.EXACTLY:
			return size;

		case MeasureSpec.AT_MOST:
			return Math.min(drawableSize, size);

		case MeasureSpec.UNSPECIFIED:
			return drawableSize;

		default:
			return size;
		}
    }

    public boolean canScrollHorizontallyFroyo(int direction) {
        return canScrollHorizontally(direction);
    }
    
    @Override
    public boolean canScrollHorizontally(int direction) {
        if (direction < 0) {
            /* See if the image overflows on the left. Working in view relative dimensions, we can scroll if the distance
             * from the image left edge to the focus point is more than the distance from the view center to the view's left edge */
            return viewDimensions.relativeWidth() / 2 < this.state.focusPoint.x * this.state.zoom;
        } else if (direction > 0) {
            /* See if the image overflows on the right. Working in view relative dimensions, we can scroll if the distance
             * from the image left edge to the focus point is more than the distance from the view center to the view's left edge */
            return viewDimensions.relativeWidth() / 2 < (drawableDimensions().relativeWidth() - this.state.focusPoint.x) * this.state.zoom;
        } else {
            // direction == 0: we can always not scroll...
            return true;
        }
    }
}