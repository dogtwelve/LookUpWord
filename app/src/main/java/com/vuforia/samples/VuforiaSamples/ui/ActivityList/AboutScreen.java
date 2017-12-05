/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.ui.ActivityList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.vuforia.samples.VuforiaSamples.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class AboutScreen extends Activity implements OnClickListener
{
    private static final String LOGTAG = "AboutScreen";
    
    private WebView mAboutWebText;
    private Button mStartButton;
    private TextView mAboutTextTitle;
    private String mClassToLaunch;
    private String mClassToLaunchPackage;
    
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
//无标题，全屏显示
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        定义了一个标题，一个webView和一个底部按钮
        setContentView(R.layout.about_screen);
//        获取上一个activtiy传递过来的数据
        Bundle extras = getIntent().getExtras();

//webView 路径ImageTargets/IT_about.html
        String webText = extras.getString("ABOUT_TEXT");
//        获取当前包的路径com.vuforia.samples.VuforiaSamples
        mClassToLaunchPackage = getPackageName();
//        需要启动的下一个activity的地址,点击imageTarget后 com.vuforia.samples.VuforiaSamples.app.ImageTargets.ImageTargets
        mClassToLaunch = mClassToLaunchPackage + "."
            + extras.getString("ACTIVITY_TO_LAUNCH");
        
        mAboutWebText = (WebView) findViewById(R.id.about_html_text);
        
        AboutWebViewClient aboutWebClient = new AboutWebViewClient();
        mAboutWebText.setWebViewClient(aboutWebClient);
        
        String aboutText = "";
        try
        {
//            获取assets文件夹下的本地文件webText = ImageTargets/IT_about.html
            InputStream is = getAssets().open(webText);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(is));
            String line;
            
            while ((line = reader.readLine()) != null)
            {
                aboutText += line;
            }
        } catch (IOException e)
        {
            Log.e(LOGTAG, "About html loading failed");
        }
//        加载数据，第一个参数是要加载的数据，第二个参数是指定text的编码方式，第三个参数指定webView的编码方式。
        mAboutWebText.loadData(aboutText, "text/html", "UTF-8");
        
        mStartButton = (Button) findViewById(R.id.button_start);
        mStartButton.setOnClickListener(this);
        
        mAboutTextTitle = (TextView) findViewById(R.id.about_text_title);
//        当前界面的显示标题
        mAboutTextTitle.setText(extras.getString("ABOUT_TEXT_TITLE"));
        
    }
    
    
    // Starts the chosen activity
    private void startARActivity()
    {
        Intent i = new Intent();
//        两个参数分别是包名和包名下的class，注意是全路径
        i.setClassName(mClassToLaunchPackage, mClassToLaunch);
        startActivity(i);
    }
    
    
    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.button_start:
                startARActivity();
                break;
        }
    }
//    网页加载机制
    private class AboutWebViewClient extends WebViewClient {
//当加载的网页需要重定向的时候就会回调这个函数告知我们应用程序是否需要接管控制网页加载，如果应用程序接管，
// 并且return true意味着主程序接管网页加载，如果返回false让webview自己处理。
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//            Intent.ACTION_VIEW：用于显示用户的数据。根据用户的数据类型来打开相应的Activity
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
    }
}
