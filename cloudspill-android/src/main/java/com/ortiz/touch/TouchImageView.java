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
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.gamboni.cloudspill.shared.util.ImageOrientationUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            return "("+ x +", "+ y +")";
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
         * angle and relative sizes if the image is not square. {@link TouchState#radius} would also be ill-defined, for the same reason. */
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

        public String toString() {
            return "State(f:"+ focusPoint +", z:"+ zoom +", r:"+ rotation +")";
        }
    }

    private static class TouchState {
        /** Which part of the image is being touched right now, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
        final Coordinate centre;
        /** The distance between two fingers, in case of two-finger touch, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
        final float radius;

        TouchState(Coordinate centre, float radius) {
            this.centre  = centre;
            this.radius = radius;
        }

        static TouchState forCoordinates(Collection<Coordinate> coordinates) {
            if (coordinates.isEmpty()) { return null; }

            /* Compute centre */
            float x=0, y=0;
            for (Coordinate c : coordinates) {
                x += c.x;
                y += c.y;
            }
            Coordinate centre = new Coordinate(
                    x / coordinates.size(),
                    y / coordinates.size());
            /* Compute size */
            float r=0;
            for (Coordinate c : coordinates) {
                r += Math.sqrt((c.x - centre.x) * (c.x - centre.x) + (c.y - centre.y) * (c.y - centre.y));
            }
            return new TouchState(centre, r/coordinates.size());
        }

        public String toString() {
            return centre +"±"+ radius;
        }
    }

    Dimensions viewDimensions;
    State state = new State(new Coordinate(0, 0), 1, 0);

    private final Context context;
    private boolean onDrawReady = false;

    /** Image Uri to load as soon as we know our size. */
    private Uri uriToLoad = null;


    private final OnTouchListener touchListener = new OnTouchListener() {

        /** In relative view coordinates, the positions of all pointers. */
        Map<Integer, Coordinate> drawablePointers = new HashMap<>();

        @Override
        public boolean onTouch(View v, MotionEvent m) {
            /* compute drawable touch state, view touch state (both on intersection of pointers) */
            /* compute state to align two touch states */
            /* save current drawable touch state (only required if the pointer set changed since last step */

            /* Whole calculation is done on the pointers that were touching in previous step, and are still touching now.
             * drawableCoords: which points, in relative drawable coordinates, were touched in last step
             */
            List<Coordinate> drawableCoords = new ArrayList<>();
            /* viewCoords: which points, in relative view coordinates are touched now */
            List<Coordinate> viewCoords = new ArrayList<>();
            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();

            float[] touchArray = new float[m.getPointerCount() * 2];
            for (int i = 0; i < m.getPointerCount(); i++) {
                int id = m.getPointerId(i);
                Coordinate drawablePointer = drawablePointers.get(id);
                m.getPointerCoords(i, c);
                touchArray[i*2] = c.x;
                touchArray[i*2 + 1] = c.y;

                if (drawablePointer != null) {
                    drawableCoords.add(drawablePointer);
                    Coordinate viewPointer = new Coordinate(c.x / viewDimensions.resolution(), c.y / viewDimensions.resolution());
                    viewCoords.add(viewPointer);
                }
            }

            final Matrix newMatrix;
            if (drawableCoords.size() > 0) {
                TouchState drawableState = TouchState.forCoordinates(drawableCoords);
                TouchState viewState = TouchState.forCoordinates(viewCoords);
                /*Log.d(TAG, "Drawable state :"+ drawableState);
                Log.d(TAG, "View state :"+ viewState);*/
                final float zoom;
                if (drawableCoords.size() == 1) {
                    // Keep current zoom when there's only one finger
                    zoom = state.zoom;
                } else {
                    Log.d(TAG, "Setting zoom to "+ viewState.radius +" / "+ drawableState.radius);
                    zoom = viewState.radius / drawableState.radius;
                }
//                Log.d(TAG, "view center: "+ viewDimensions.relativeWidth() / 2 +", "+ viewDimensions.relativeHeight() / 2);
                // TODO probably won't work when rotation ≠ 0
                state = new State(new Coordinate(
                        drawableState.centre.x + (viewDimensions.relativeWidth() / 2 - viewState.centre.x) / zoom,
                        drawableState.centre.y + (viewDimensions.relativeHeight() / 2 - viewState.centre.y) / zoom),
                        zoom,
                        state.rotation);
                newMatrix = computeMatrix();

 //               debug("touchEvent setting matrix ", newMatrix);
                setImageMatrix(newMatrix);
            } else {
                newMatrix = getImageMatrix();
            }

            Matrix viewToDraw = new Matrix();
            newMatrix.invert(viewToDraw);
            viewToDraw.mapPoints(touchArray);
            final int resolution = drawableDimensions().resolution();
            Map<Integer, Coordinate> newPointers = new HashMap<>();
            if (m.getAction() != MotionEvent.ACTION_UP) {
                for (int i = 0; i < m.getPointerCount(); i++) {
                    newPointers.put(m.getPointerId(i), new Coordinate(touchArray[2*i] / resolution, touchArray[2*i + 1]  / resolution));
                }
            } // else: "action up" causes state to be cleared
            this.drawablePointers = newPointers;
            return true;
        }
    };

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
        setOnTouchListener(touchListener);
        super.setSaveEnabled(true);
        Log.d(TAG, "New instance");
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
        Log.d(TAG, "Saving instance state"+ state);
    	Bundle bundle = new Bundle();
    	bundle.putParcelable("instanceState", super.onSaveInstanceState());
    	bundle.putParcelable("state", state);
    	return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        Log.d(TAG, "Restoring instance state");
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
            this.state = bundle.getParcelable("state");
            Log.d(TAG, "Got state "+ state);
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
        Dimensions rotatedDrawable = widthHeightFlipped(state.rotation) ? drawableDimensions.flipped() : drawableDimensions;

        /* Compute zoom to have entire image visible */
        float zoom = Math.min(viewDimensions.relativeWidth() / rotatedDrawable.relativeWidth(),
            viewDimensions.relativeHeight() / rotatedDrawable.relativeHeight());
        this.state = new State(
                /* Centre */
                drawableDimensions.relativeCenter(),
                zoom,
                this.state.rotation);
        Log.d(TAG, "fitImageToView setting state " + this.state);

        Matrix m = computeMatrix();

        debug("fitImageToView setting matrix ", m);
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(m);
    }

    @NonNull
    private Matrix computeMatrix() {
        Dimensions drawableDimensions = drawableDimensions();

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
        return m;
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