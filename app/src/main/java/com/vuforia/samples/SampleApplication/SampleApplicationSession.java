/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.samples.SampleApplication;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;
//摄像头校准
import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Matrix44F;
//一个4*4的矩阵，用于记录坐标变，是计算机图形学的基础
//一个空间的点可以用一个1*4的行向量表示（x,y,z,1）,最后一个1表示的是比例，若为2，表示坐标为（2x,2y,2z）,
// 传送门http://379910987.blog.163.com/blog/static/335237972010111010363383/
//http://www.cnblogs.com/TianFang/p/3920734.html
//http://wenku.baidu.com/link?url=Obq5QfYY5LDgBNWz1dC1IICLl05kEX44lwJgW6CGvdTHv75qHJla40upMduy_nTxxo0g5Eb7Yxbe-qLbBCk2w4Gn2JapdtrbHWKDgpZMB_y
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Vec2I;//vec为容器，2表示2D，I表示int,一种数据结构，后面的vec4f类似
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;
import com.vuforia.samples.VuforiaSamples.R;


public class SampleApplicationSession implements UpdateCallbackInterface
{
    
    private static final String LOGTAG = "VuforiaApplications";
    
    // Reference to the current activity
    private Activity mActivity;
    private SampleApplicationControl mSessionControl;
    
    // Flags
    private boolean mStarted = false;
    private boolean mCameraRunning = false;
    
    // Display size of the device:
    //注意后面的方法，这里是设备的显示器宽高，我们使用摄像头的时候其实摄像头图像的显示在显示屏上
    // 只占了大部分，并不是全屏，而我们使用vuforia样例的时候，显示的背景是全屏的，
    // 故我们要将摄像头获得的图片进行变换成全屏的图片在显示在手机上
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    
    // The async tasks to initialize the Vuforia SDK:
    //async tasks为异步任务，用于处理UI线程外的工作，这里用于进行vuforia的初始化
    private InitVuforiaTask mInitVuforiaTask;
    private LoadTrackerTask mLoadTrackerTask;
    
    // An object used for synchronizing Vuforia initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia:
    private Object mShutdownLock = new Object();
    
    // Vuforia initialization flags:
    private int mVuforiaFlags = 0;
    
    // Holds the camera configuration to use upon resuming
    //CAMERA_DIRECTION摄像头的设备，CAMERA_DIRECTION_DEFAULT默认摄像头
    //CAMERA_DIRECTION_FRONT前摄像头，CAMERA_DIRECTION_BACK后摄像头
    private int mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;
//    投射矩阵
    // Stores the projection matrix to use for rendering purposes
    private Matrix44F mProjectionMatrix;
//视图矩阵
    // Stores viewport to be used for rendering purposes
    private int[] mViewport;
//方向
    // Stores orientation
    private boolean mIsPortrait = false;
    
    
    public SampleApplicationSession(SampleApplicationControl sessionControl)
    {
        //most important构造函数
        // SampleApplicationControl为接口
        // 参数传进来都是一个activiy对象，这些对象都继承了control接口
        // 故这些对象可以上转型赋值给一个control接口
        //mSessionContol就是用来调用activity实现SampleApplicationControl里面的函数，起到一个中介的作用
        //把不同的actvity赋值给mSessionControl，就能调用各自不同的实现方法
        mSessionControl = sessionControl;
    }
    
    
    // Initializes Vuforia and sets up preferences.
    public void initAR(Activity activity, int screenOrientation)
    {
        SampleApplicationException vuforiaException = null;
        mActivity = activity;
        Log.i(LOGTAG,"initAR");
       // ActivityInfo.SCREEN_ORIENTATION_SENSOR,//由物理感应器决定显示方向
        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)

            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        //设置全屏转向
        //ECLAIR_0_1 December 2009: Android 2.0.1实际上在判断讲平台的版本跟应用的版本进行对比
     /*   ECLAIR_MR1 January 2010: Android 2.1      与Android June Android 2.2进行比较
        FROYO June 2010: Android 2.2
        GINGERBREAD November 2010: Android 2.3
        GINGERBREAD_MR1 February 2011: Android 2.3.3.
            HONEYCOMB February 2011: Android 3.0.
            HONEYCOMB_MR1 May 2011: Android 3.1.
            HONEYCOMB_MR2 June 2011: Android 3.2.
            ICE_CREAM_SANDWICH Android 4.0.*/
        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        OrientationEventListener orientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int i) {
                int activityRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                if(mLastRotation != activityRotation)
                {
                    // Signal the ApplicationSession to refresh the projection matrix
                    setProjectionMatrix();
                    mLastRotation = activityRotation;
                }
            }
//            此处的mLastRotation是先在上面引用再在下面定义的，这个本人不理解，有知道的朋友可以解释下这种方式，这种方式编译没错，但好像得不到想要的结果。
            int mLastRotation = -1;
        };
        
        if(orientationEventListener.canDetectOrientation())
            orientationEventListener.enable();

        // Apply screen orientation
        mActivity.setRequestedOrientation(screenOrientation);
//        确认屏幕的朝向
        updateActivityOrientation();
//        获取屏幕边界及大小
        // Query display dimensions:
        storeScreenDimensions();
        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        mActivity.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        mVuforiaFlags = Vuforia.GL_20;//常量2
        
        // Initialize Vuforia SDK asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(
                SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                logMessage);
            Log.e(LOGTAG, logMessage);
        }
        
        if (vuforiaException == null)
        {
            try
            {
                Log.i(LOGTAG,"新建一个InitVuforiaTask to init");
                mInitVuforiaTask = new InitVuforiaTask();
                mInitVuforiaTask.execute();
            } catch (Exception e)
            {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }
        //
        if (vuforiaException != null)
            mSessionControl.onInitARDone(vuforiaException);
    }
    
    
    // Starts Vuforia, initialize and starts the camera and start the trackers
    public void startAR(int camera) throws SampleApplicationException
    {
        String error;
        Log.i(LOGTAG,"startAR");
        if(mCameraRunning)
        {
        	error = "Camera already running, unable to open again";
        	Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mCamera = camera;
        if (!CameraDevice.getInstance().init(camera))
        {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
               
        if (!CameraDevice.getInstance().selectVideoMode(
            CameraDevice.MODE.MODE_DEFAULT))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        // Configure the rendering of the video background
          configureVideoBackground();

        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        setProjectionMatrix();
        
        mSessionControl.doStartTrackers();
        
        mCameraRunning = true;
        
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
        }
    }
    
    
    // Stops any ongoing initialization, stops Vuforia
    public void stopAR() throws SampleApplicationException
    {
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
            && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }
        
        if (mLoadTrackerTask != null
            && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        mInitVuforiaTask = null;
        mLoadTrackerTask = null;
        
        mStarted = false;
        
        stopCamera();
        
        // Ensure that all asynchronous operations to initialize Vuforia
        // and loading the tracker datasets do not overlap:
        synchronized (mShutdownLock)
        {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControl.doUnloadTrackersData();
            
            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControl.doDeinitTrackers();
            
            // Deinitialize Vuforia SDK:
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
            
        }
    }
    
    
    // Resumes Vuforia, restarts the trackers and the camera
    public void resumeAR() throws SampleApplicationException
    {
        // Vuforia-specific resume operation
        Vuforia.onResume();
        
        if (mStarted)
        {
            startAR(mCamera);
        }
    }
    
    
    // Pauses Vuforia and stops the camera
    public void pauseAR() throws SampleApplicationException
    {
        if (mStarted)
        {
            stopCamera();
        }
        
        Vuforia.onPause();
    }
    
    
    // Gets the projection matrix to be used for rendering
    public Matrix44F getProjectionMatrix()
    {
        return mProjectionMatrix;
    }

    // Gets the viewport to be used fo rendering
    public int[] getViewport()
    {
        return mViewport;
    }
    
    // Callback called every cycle
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControl.onVuforiaUpdate(s);
    }
    
    
    // Manages the configuration changes
    public void onConfigurationChanged()
    {
        updateActivityOrientation();
        Log.i(LOGTAG, "oh no");
        storeScreenDimensions();
        
        if (isARRunning())
        {
            // configure video background
            configureVideoBackground();
            
            // Update projection matrix:
            setProjectionMatrix();
        }
        
    }
    
    
    // Methods to be called to handle lifecycle
    public void onResume()
    {
        Vuforia.onResume();
    }
    
    
    public void onPause()
    {
        Vuforia.onPause();
    }
    
    
    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }
    
    
    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }
    
    // An async task to initialize Vuforia asynchronously.
    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value:
        private int mProgressValue = -1;
        private static final String INITLOGTAG = "VSA_InitVuforiaTask";
        protected Boolean doInBackground(Void... params)
        {
            Log.i(INITLOGTAG,"doInBackground");
            // Prevent the onDestroy() method to overlap with initialization:
            //doInBackground(Params…) 后台执行，比较耗时的操作都可以放在这里。注意这里不能直接操作UI。
            // 此方法在后台线程执行，完成任务的主要工作，通常需要较长的时间。在执行过程中可以调用
            //传送门http://www.cnblogs.com/devinzhang/archive/2012/02/13/2350070.html
            synchronized (mShutdownLock)
            {
                Vuforia.setInitParameters(mActivity, mVuforiaFlags, "ASPiXrn/////AAAAAdymojVJSUJ9mu0kFcjL3rcVJv0vcEvxrFMThuZgfARTAhy046g7KqnRL7ZjWa4uCbX6BnTd8B+BTR8wbSOa3PXkuR380/ToqIiqUE+cQ0Xfh0MuOBb97R5WypEd0CG8mjIpgIjplcSyny+5mklX2rZESMuyqDvtRk9DgraAjsZ+QLMyD+TpsWcmrMCTPmXSg4rnHorjRD/iw/ZfVS9seCr3qIBUN4lGPnWVV+1n3KNz7gzf+BYohjaszs1IzpMKjQLEu5xCGXN/MizKD+kXzUuowYq6SN2kdljlxEhXGw1yBUtN9lRVPJPZf40YU6MmGTLJWpLY2Gjx1DuHWuljqBXQXzV1PJOhfoQpBYjwYepy");
                
                do
                {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();
                    
                    // Publish the progress value:
                    publishProgress(mProgressValue);
                    
                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                    //当没有被取消，并且完成百分比在0-100时继续初始化
                } while (!isCancelled() && mProgressValue >= 0
                    && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }
        
        
        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }
       // onPostExecute(Result)  相当于Handler 处理UI的方式，在这里面可以使用在doInBackground
       // 得到的结果处理操作UI。 此方法在主线程执行，任务执行的结果作为此方法的参数返回
        
        protected void onPostExecute(Boolean result)
        {
            // Done initializing Vuforia, proceed to next application
            // initialization status:
            Log.i(INITLOGTAG,"onPostExecute");
            SampleApplicationException vuforiaException = null;
            
            if (result)
            {
                //下面也把Log.d进行替换
           /*     Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia "
                    + "initialization successful");*/
                Log.i(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia "
                        + "initialization successful");
                
                boolean initTrackersResult;
                initTrackersResult = mSessionControl.doInitTrackers();
                
                if (initTrackersResult)
                {
                    try
                    {
                        mLoadTrackerTask = new LoadTrackerTask();
                        mLoadTrackerTask.execute();
                    } catch (Exception e)
                    {
                        String logMessage = "Loading tracking data set failed";
                        vuforiaException = new SampleApplicationException(
                            SampleApplicationException.LOADING_TRACKERS_FAILURE,
                            logMessage);
                        Log.e(LOGTAG, logMessage);
                        mSessionControl.onInitARDone(vuforiaException);
                    }
                    
                } else
                {
                    vuforiaException = new SampleApplicationException(
                        SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        "Failed to initialize trackers");
                    mSessionControl.onInitARDone(vuforiaException);
                }
            } else
            {
                String logMessage;
                
                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = getInitializationErrorString(mProgressValue);
                
                // Log error:
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage
                    + " Exiting.");
                
                // Send Vuforia Exception to the application and call initDone
                // to stop initialization process
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                mSessionControl.onInitARDone(vuforiaException);
            }
        }
    }
    
    // An async task to load the tracker data asynchronously.
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {

        protected Boolean doInBackground(Void... params)
        {
            Log.i(LOGTAG,"in LoadTrackerTask doInBackground");
            // Prevent the onDestroy() method to overlap:
            synchronized (mShutdownLock)
            {
                // Load the tracker data set:
                return mSessionControl.doLoadTrackersData();
            }
        }
        
        
        protected void onPostExecute(Boolean result)
        {
            Log.i(LOGTAG,"I am in onpostExcute");
            SampleApplicationException vuforiaException = null;
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                // Error loading dataset
                Log.e(LOGTAG, logMessage);
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.LOADING_TRACKERS_FAILURE,
                    logMessage);
            } else
            {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();
                Log.i(LOGTAG,"registerCallback");
                Vuforia.registerCallback(SampleApplicationSession.this);
                
                mStarted = true;
            }
            
            // Done loading the tracker, update application status, send the
            // exception to check errors
            mSessionControl.onInitARDone(vuforiaException);
        }
    }
    
    
    // Returns the error message for each error code
    private String getInitializationErrorString(int code)
    {
        if (code == Vuforia.INIT_DEVICE_NOT_SUPPORTED)
            return mActivity.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == Vuforia.INIT_NO_CAMERA_ACCESS)
            return mActivity.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == Vuforia.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
        {
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }
    
    
    // Stores screen dimensions
    private void storeScreenDimensions()
    {
        //获取屏幕大小边界，宽和高
        // Query display dimensions:
        Log.i(LOGTAG,"storeScreenDimensions");
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        //资料网站http://blog.csdn.net/yujian_bing/article/details/8264780
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
    }
    
    
    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivity.getResources().getConfiguration();
        Log.i(LOGTAG,"updateActivityOrientation");
        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }
        
        Log.i(LOGTAG, "Activity is in "
            + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
    
    
    // Method for setting / updating the projection matrix for AR content
    // rendering
    //“径向畸变”就是矢量端点沿长度方向发生的变化dr,也就是矢径的变化。
    //学术文档http://www.uni-koeln.de/~al001/radcor_files/hs100.htm
    public void setProjectionMatrix()
    {
        Log.i(LOGTAG,"setProjectionMatrix");
        CameraCalibration camCal = CameraDevice.getInstance().getCameraCalibration();
        //四行四列的矩阵
        mProjectionMatrix = Tool.getProjectionGL(camCal, 10.0f, 5000.0f);
        //(const CameraCalibration &calib, float nearPlane, float farPlane)
       // Returns an OpenGL style projection
        // MATRIX矩阵 .http://www.zhihu.com/question/23128374
        //http://www.songho.ca/opengl/gl_projectionmatrix.html


    }
    
    
    public void stopCamera()
    {
        if(mCameraRunning)
        {
            mSessionControl.doStopTrackers();
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
            mCameraRunning = false;
        }
    }
    
    
    // Configures the video mode and sets offsets for the camera's image
    //配置录像模式和设置摄像机图片的偏移量
    private void configureVideoBackground()
    {
        Log.i(LOGTAG, "configureVideoBackground");
        CameraDevice cameraDevice = CameraDevice.getInstance();
        //CameraDevice有三种模式，
        // 一种是 MODE_DEFAULT：速度和效率之间。
        // MODE_OPTIMIZE_SPEED：速度优先。
        // MODE_OPTIMIZE_QUALITY：质量优先。
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);
        
        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        //Vec2I,2维的Int类型向量，0,0表示原点
        config.setPosition(new Vec2I(0, 0));
        
        int xSize = 0, ySize = 0;
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                .getWidth()));
            ySize = mScreenHeight;
            
            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                    .getHeight()));
            }
        } else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                .getWidth()));
            
            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                    .getHeight()));
                ySize = mScreenHeight;
            }
        }
        //设置背景大小为手机的分辨率
        config.setSize(new Vec2I(xSize, ySize));

        // The Vuforia VideoBackgroundConfig takes the position relative to the
        // centre of the screen, where as the OpenGL glViewport call takes the
        // position relative to the lower left corner
        mViewport = new int[4];
        mViewport[0] = ((mScreenWidth - xSize) / 2) + config.getPosition().getData()[0];
        mViewport[1] = ((mScreenHeight - ySize) / 2) + config.getPosition().getData()[1];
        mViewport[2] = xSize;
        mViewport[3] = ySize;

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
            + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
            + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");
        //i7设备主屏分辨率：1920x1080像素，对应为msize
        Renderer.getInstance().setVideoBackgroundConfig(config);
        
    }
    
    // Returns true if Vuforia is initialized, the trackers started and the
    // tracker data loaded
    private boolean isARRunning()
    {
        return mStarted;
    }
}
