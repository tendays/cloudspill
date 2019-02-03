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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.OverScroller;

import org.gamboni.cloudspill.shared.util.ImageOrientationUtil;

import java.io.IOException;

public class TouchImageView extends ImageView {
    private static final String TAG = "TIV";

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
    State state;

    // Handling of touch state
    /** Which part of the image is being touched right now, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
    Coordinate touchPosition;

    /** The distance between two fingers, in case of two-finger touch, in relative image coordinates (see {@link State#focusPoint} documentation for definition). */
    float touchDistance;

    private final Context context;
    private boolean onDrawReady = false;

    /** Image Uri to load as soon as we know our size. */
    private Uri uriToLoad = null;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private View.OnTouchListener userTouchListener = null;

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
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        super.setOnTouchListener(new PrivateOnTouchListener());
    }

    @Override
    public void setOnTouchListener(View.OnTouchListener l) {
        userTouchListener = l;
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
    
    /**
     * Returns false if image is in initial, unzoomed state. False, otherwise.
     * @return true if image is zoomed
     */
    public boolean isZoomed() {
    	return state.zoom != 1;
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
            state = bundle.getParcelable("state");
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

                        state = state.withRotation(ImageOrientationUtil.getExifRotation(context.getContentResolver().openInputStream(uri)));

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

    private boolean widthHeightFlipped(int orientation) {
        return (orientation == 6 || orientation == 8);
    }

    private float getRotation(int orientation) {
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

        /* Compute zoom to have entire image visible */
        float zoom = Math.min(viewDimensions.relativeWidth() / drawableDimensions.relativeWidth(),
            viewDimensions.relativeHeight() / drawableDimensions.relativeHeight());

        this.state = new State(
                /* Centre */
                drawableDimensions.relativeCenter(),
                zoom,
                /* TODO rotation based on orientation */
                0);

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

        debug(m);
        setImageMatrix(m);
    }

    private void debug(Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);

        Log.d(TAG, "[("+ values[0] +" "+ values[1] +" "+ values[2] +") ("+ values[3] +" "+ values[4] +" "+ values[5] +") ("+ values[6] +" "+ values[7] +" "+ values[8] +")]");
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

    /**
     * Gesture Listener detects a single click or long click and passes that on
     * to the view's listener.
     * @author Ortiz
     *
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
    	
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e)
        {
        	return performClick();
        }
        
        @Override
        public void onLongPress(MotionEvent e)
        {
        	performLongClick();
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
        		//
        		// If a previous fling is still active, it should be cancelled so that two flings
        		// are not run simultaneously.
        		//

        	Fling fling = new Fling((int) velocityX, (int) velocityY);
        	compatPostOnAnimation(fling);
        	return super.onFling(e1, e2, velocityX, velocityY);
        }
        
        @Override
        public boolean onDoubleTap(MotionEvent e) {
        	boolean consumed = false;
            /*if(doubleTapListener != null) {
            	consumed = doubleTapListener.onDoubleTap(e);
            }
        	if (state == State.NONE) {
	        	float targetZoom = (normalizedScale == minScale) ? maxScale : minScale;
	        	DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, e.getX(), e.getY(), false);
	        	compatPostOnAnimation(doubleTap);
	        	consumed = true;
        	}*/
        	return consumed;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            /*if(doubleTapListener != null) {
            	return doubleTapListener.onDoubleTapEvent(e);
            }*/
            return false;
        }
    }
    
    public interface OnTouchImageViewListener {
    	public void onMove();
    }
    
    /**
     * Responsible for all touch events. Handles the heavy lifting of drag and also sends
     * touch events to Scale Detector and Gesture Detector.
     * @author Ortiz
     *
     */
    private class PrivateOnTouchListener implements OnTouchListener {
    	
    	//
        // Remember last point position for dragging
        //
        private PointF last = new PointF();
    	
    	@Override
        public boolean onTouch(View v, MotionEvent event) {
            /*scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            PointF curr = new PointF(event.getX(), event.getY());
            
            if (state == State.NONE || state == State.DRAG || state == State.FLING) {
	            switch (event.getAction()) {
	                case MotionEvent.ACTION_DOWN:
	                	last.set(curr);
	                    if (fling != null)
	                    	fling.cancelFling();
	                    setState(State.DRAG);
	                    break;
	                    
	                case MotionEvent.ACTION_MOVE:
	                    if (state == State.DRAG) {
	                        float deltaX = curr.x - last.x;
	                        float deltaY = curr.y - last.y;
	                        float fixTransX = getFixDragTrans(deltaX, viewWidth, getImageWidth());
	                        float fixTransY = getFixDragTrans(deltaY, viewHeight, getImageHeight());
	                        matrix.postTranslate(fixTransX, fixTransY);
	                        fixTrans();
	                        last.set(curr.x, curr.y);
	                    }
	                    break;
	
	                case MotionEvent.ACTION_UP:
	                case MotionEvent.ACTION_POINTER_UP:
	                    setState(State.NONE);
	                    break;
	            }
            }
            
            setImageMatrix(matrix);
            
            //
    		// User-defined OnTouchListener
    		//
    		if(userTouchListener != null) {
    			userTouchListener.onTouch(v, event);
    		}
            
    		//
    		// OnTouchImageViewListener is set: TouchImageView dragged by user.
    		//
    		if (touchImageViewListener != null) {
    			touchImageViewListener.onMove();
    		}
    		
            //
            // true would indicate event was handled
            //
            */
            return false;
        }
    }

    /**
     * ScaleListener detects user two finger scaling and scales image.
     * @author Ortiz
     *
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            //setState(State.ZOOM);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
        	//scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY(), true);
        	
        	//
        	// OnTouchImageViewListener is set: TouchImageView pinch zoomed by user.
        	//
        	/*if (touchImageViewListener != null) {
        		touchImageViewListener.onMove();
        	}*/
            return true;
        }
        
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        	super.onScaleEnd(detector);
        	/*setState(State.NONE);
        	boolean animateToZoomBoundary = false;
        	float targetZoom = normalizedScale;
        	if (normalizedScale > maxScale) {
        		targetZoom = maxScale;
        		animateToZoomBoundary = true;
        		
        	} else if (normalizedScale < minScale) {
        		targetZoom = minScale;
        		animateToZoomBoundary = true;
        	}
        	
        	if (animateToZoomBoundary) {
	        	DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, viewWidth / 2, viewHeight / 2, true);
	        	compatPostOnAnimation(doubleTap);
        	}*/
        }
    }
    
    /**
     * DoubleTapZoom calls a series of runnables which apply
     * an animated zoom in/out graphic to the image.
     * @author Ortiz
     *
     */
    private class DoubleTapZoom implements Runnable {
    	
    	private long startTime;
    	private static final float ZOOM_TIME = 500;
    	private float startZoom, targetZoom;
    	private float bitmapX, bitmapY;
    	private boolean stretchImageToSuper;
    	private AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
    	private PointF startTouch;
    	private PointF endTouch;

    	DoubleTapZoom(float targetZoom, float focusX, float focusY, boolean stretchImageToSuper) {
    		/*setState(State.ANIMATE_ZOOM);
    		startTime = System.currentTimeMillis();
    		this.startZoom = normalizedScale;
    		this.targetZoom = targetZoom;
    		this.stretchImageToSuper = stretchImageToSuper;
    		PointF bitmapPoint = transformCoordTouchToBitmap(focusX, focusY, false);
    		this.bitmapX = bitmapPoint.x;
    		this.bitmapY = bitmapPoint.y;
    		
    		//
    		// Used for translating image during scaling
    		//
    		startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY);
    		endTouch = new PointF(viewWidth / 2, viewHeight / 2);*/
    	}

		@Override
		public void run() {
/*			float t = interpolate();
			double deltaScale = calculateDeltaScale(t);
			scaleImage(deltaScale, bitmapX, bitmapY, stretchImageToSuper);
			translateImageToCenterTouchPosition(t);
			//fixScaleTrans();
			setImageMatrix(matrix);
			
			//
			// OnTouchImageViewListener is set: double tap runnable updates listener
			// with every frame.
			//
			if (touchImageViewListener != null) {
				touchImageViewListener.onMove();
			}
			
			if (t < 1f) {
				//
				// We haven't finished zooming
				//
				compatPostOnAnimation(this);
				
			} else {
				//
				// Finished zooming
				//
				setState(State.NONE);
			}
			*/
		}
    }
    
    /**
     * Fling launches sequential runnables which apply
     * the fling graphic to the image. The values for the translation
     * are interpolated by the Scroller.
     * @author Ortiz
     *
     */
    private class Fling implements Runnable {
    	
        OverScroller scroller;
    	int currX, currY;
    	
    	Fling(int velocityX, int velocityY) {
    /*		setState(State.FLING);
    		scroller = new OverScroller(context);
    		matrix.getValues(m);
    		
    		int startX = (int) m[Matrix.MTRANS_X];
    		int startY = (int) m[Matrix.MTRANS_Y];
    		int minX, maxX, minY, maxY;
    		
    		if (getImageWidth() > viewWidth) {
    			minX = viewWidth - (int) getImageWidth();
    			maxX = 0;
    			
    		} else {
    			minX = maxX = startX;
    		}
    		
    		if (getImageHeight() > viewHeight) {
    			minY = viewHeight - (int) getImageHeight();
    			maxY = 0;
    			
    		} else {
    			minY = maxY = startY;
    		}
    		
    		scroller.fling(startX, startY, (int) velocityX, (int) velocityY, minX,
                    maxX, minY, maxY);
    		currX = startX;
    		currY = startY;
    */	}
    	
    	public void cancelFling() {
    /*		if (scroller != null) {
    			setState(State.NONE);
    			scroller.forceFinished(true);
    		}
    */	}
    	
		@Override
		public void run() {
			
			//
			// OnTouchImageViewListener is set: TouchImageView listener has been flung by user.
			// Listener runnable updated with each frame of fling animation.
			//
	/*		if (touchImageViewListener != null) {
				touchImageViewListener.onMove();
			}
			
			if (scroller.isFinished()) {
        		scroller = null;
        		return;
        	}
			
			if (scroller.computeScrollOffset()) {
	        	int newX = scroller.getCurrX();
	            int newY = scroller.getCurrY();
	            int transX = newX - currX;
	            int transY = newY - currY;
	            currX = newX;
	            currY = newY;
	            matrix.postTranslate(transX, transY);
	            //fixTrans();
	            setImageMatrix(matrix);
	            compatPostOnAnimation(this);
        	}
	*/	}
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void compatPostOnAnimation(Runnable runnable) {
            postOnAnimation(runnable);
    }
}