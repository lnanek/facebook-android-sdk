package com.facebook.android.support;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebView;

public class NoNPEWebView extends WebView {
	
	private static final String LOG_TAG = "NoNPEWebView";
	
	public NoNPEWebView(Context context) {
		super(context);
	}

	public NoNPEWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public NoNPEWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch(NullPointerException e) {
        	Log.e(LOG_TAG, "Ignoring exception thrown by WebView#onWindowFocusChanged.", e);
        }
    }
}
