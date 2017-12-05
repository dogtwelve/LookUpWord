/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.SampleApplication.utils;

import android.opengl.GLES20;
import android.util.Log;


public class SampleUtils
{

    private static final String LOGTAG = "VuforiaApplications";
    
    //传送门http://www.tuicool.com/articles/VZVJra
    static int initShader(int shaderType, String source)
    {
        int shader = GLES20.glCreateShader(shaderType);
     /* 步骤1：申请特定着色器
       shaderType  GLES20.GL_VERTEX_SHADER(顶点)   GLES20.GL_FRAGMENT_SHADER(片元)
       如果申请成功则返回的shaderId不为零
       下面为申请着色器成功*/
        if (shader != 0)
        {
            //步骤2：给申请成功的着色器加载脚本并编译,查错
            GLES20.glShaderSource(shader, source);/// source是脚本字符串，是函数的参数
            GLES20.glCompileShader(shader);///编译
            
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
                    glStatusVar, 0);
            //查看编译情况，函数原型：
          /*  void glGetProgramiv (int program, int pname, int[] params, int offset)*/
            // param是返回值
            if (glStatusVar[0] == GLES20.GL_FALSE)//编译失败,释放申请的着色器
            {
                Log.i(LOGTAG,"apply shader failed");
                Log.e(LOGTAG, "Could NOT compile shader " + shaderType + " : "
                    + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            
        }
        
        return shader;
    }
    
    //创建着色器代码段， glCreateProgram，在连接shader之前，首先要创建一个容纳程序的容器，
    // 称为着色器程序容器。可以通过glCreateProgram函数来创建一个程序容器。
    public static int createProgramFromShaderSrc(String vertexShaderSrc,
        String fragmentShaderSrc)
    {
        //见上面，此处为申请一个shader容器，GL_VERTEX_SHADER顶点，GL_FRAGMENT_SHADER片元
        int vertShader = initShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc);
        int fragShader = initShader(GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderSrc);
        
        if (vertShader == 0 || fragShader == 0)
            return 0;//上面返回值vertShader，fragShader为0即为申请失败，结束此函数
        //创建着色器程序
        int program = GLES20.glCreateProgram();
        //若程序创建成功则向程序中加入顶点着色器与片元着色器
        if (program != 0)
        {
            GLES20.glAttachShader(program, vertShader);
            checkGLError("glAttchShader(vert)");
           // 向程序中加入顶点着色器
            GLES20.glAttachShader(program, fragShader);
            checkGLError("glAttchShader(frag)");
            //向程序中加入片元着色器
            GLES20.glLinkProgram(program);
            //链接程序
           // 存放链接成功program 数量的数组
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, glStatusVar,
                0);//获取program的链接情况，若链接失败则报错并删除程序
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(
                    LOGTAG,
                    "Could NOT link program : "
                        + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        // 释放shader资源，该部分源码为网上样例中出现，非本程序所有
      /*  GLES20.glDeleteShader(vertShader);
        GLES20.glDeleteShader(fragShader);*/
        //经测试，上述代码加入后并不影响程序运行
        return program;
    }
    
    
    public static void checkGLError(String op)
    {
        //https://www.opengl.org/wiki/OpenGL_Error
        //识别图形后，这里一直报错
        for (int error = GLES20.glGetError(); error != 0; error = GLES20
            .glGetError())
            Log.e(
                LOGTAG,
                "After operation " + op + " got glError 0x"
                    + Integer.toHexString(error));
    }
    
    
    // Transforms a screen pixel to a pixel onto the camera image,
    // taking into account e.g. cropping of camera image to fit different aspect
    // ratio screen.
    // for the camera dimensions, the width is always bigger than the height
    // (always landscape orientation)
    // Top left of screen/camera is origin
    public static void screenCoordToCameraCoord(int screenX, int screenY,
        int screenDX, int screenDY, int screenWidth, int screenHeight,
        int cameraWidth, int cameraHeight, int[] cameraX, int[] cameraY,
        int[] cameraDX, int[] cameraDY, int displayRotation, int cameraRotation)
    {
        float videoWidth, videoHeight;
        videoWidth = (float) cameraWidth;
        videoHeight = (float) cameraHeight;

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        int correctedRotation = ((((displayRotation*90)-cameraRotation)+360)%360)/90;

        switch (correctedRotation) {

            case 0:
                break;

            case 1:

                int tmp = screenX;
                screenX = screenHeight - screenY;
                screenY = tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;

            case 2:
                screenX = screenWidth - screenX;
                screenY = screenHeight - screenY;
                break;

            case 3:

                tmp = screenX;
                screenX = screenY;
                screenY = screenWidth - tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;
        }
        
        float videoAspectRatio = videoHeight / videoWidth;
        float screenAspectRatio = (float) screenHeight / (float) screenWidth;
        
        float scaledUpX;
        float scaledUpY;
        float scaledUpVideoWidth;
        float scaledUpVideoHeight;
        
        if (videoAspectRatio < screenAspectRatio)
        {
            // the video height will fit in the screen height
            scaledUpVideoWidth = (float) screenHeight / videoAspectRatio;
            scaledUpVideoHeight = screenHeight;
            scaledUpX = (float) screenX
                + ((scaledUpVideoWidth - (float) screenWidth) / 2.0f);
            scaledUpY = (float) screenY;
        } else
        {
            // the video width will fit in the screen width
            scaledUpVideoHeight = (float) screenWidth * videoAspectRatio;
            scaledUpVideoWidth = screenWidth;
            scaledUpY = (float) screenY
                + ((scaledUpVideoHeight - (float) screenHeight) / 2.0f);
            scaledUpX = (float) screenX;
        }
        
        if (cameraX != null && cameraX.length > 0)
        {
            cameraX[0] = (int) ((scaledUpX / (float) scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraY != null && cameraY.length > 0)
        {
            cameraY[0] = (int) ((scaledUpY / (float) scaledUpVideoHeight) * videoHeight);
        }
        
        if (cameraDX != null && cameraDX.length > 0)
        {
            cameraDX[0] = (int) (((float) screenDX / (float) scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraDY != null && cameraDY.length > 0)
        {
            cameraDY[0] = (int) (((float) screenDY / (float) scaledUpVideoHeight) * videoHeight);
        }
    }
    
    
    public static float[] getOrthoMatrix(float nLeft, float nRight,
        float nBottom, float nTop, float nNear, float nFar)
    {
        float[] nProjMatrix = new float[16];
        
        int i;
        for (i = 0; i < 16; i++)
            nProjMatrix[i] = 0.0f;
        
        nProjMatrix[0] = 2.0f / (nRight - nLeft);
        nProjMatrix[5] = 2.0f / (nTop - nBottom);
        nProjMatrix[10] = 2.0f / (nNear - nFar);
        nProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        nProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        nProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        nProjMatrix[15] = 1.0f;
        
        return nProjMatrix;
    }
    
}
