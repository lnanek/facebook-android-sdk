/*
 * Copyright 2010 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.android;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.support.DialogUtil;
import com.facebook.android.support.NoNPEWebView;

public class FbDialog extends Dialog {

    static final int FB_BLUE = 0xFF6D84B4;
    static final FrameLayout.LayoutParams FILL =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                         ViewGroup.LayoutParams.FILL_PARENT);
    static final int MARGIN = 4;
    static final int PADDING = 2;
    static final String DISPLAY_STRING = "touch";
    static final String FB_ICON = "icon.png";

    private String mUrl;
    private DialogListener mListener;
    private ProgressDialog mSpinner;
    private WebView mWebView;
    private LinearLayout mContent;
    private TextView mTitle;

    public FbDialog(Context context, String url, DialogListener listener) {
    	// Make dialog full size with no titlebar. Fixed size was cutting some things off. -Lance
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mUrl = url;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpinner = new ProgressDialog(getContext());
        mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // If previously saved authorization was used, user wasn't certain this was Facebook that was loading. So added name. -Lance
        mSpinner.setMessage("Loading Facebook...");
        // Cancel listener added to spinner to cancel posting to Facebook. 
        // Was just dismissing spinner previously. -Lance
        mSpinner.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				FbDialog.this.cancel();
			}
		});

    	// Make dialog full size with no titlebar. Fixed size was cutting some things off. -Lance
        mContent = new LinearLayout(getContext());
        mContent.setOrientation(LinearLayout.VERTICAL);
        setUpTitle();
        setUpWebView();
        addContentView(mContent, FILL);        
        
        //XXX The Facebook SDK wasn't sending anything to the listener when a dialog was left by the back button.
        // This fixes that, allowing the calling activity to react. -Lance
		this.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mListener.onCancel();
			}
		});
    }

    private void setUpTitle() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Drawable icon = getContext().getResources().getDrawable(
                R.drawable.facebook_icon);
        mTitle = new TextView(getContext());
        mTitle.setText("Facebook");
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setBackgroundColor(FB_BLUE);
        mTitle.setPadding(MARGIN + PADDING, MARGIN, MARGIN, MARGIN);
        mTitle.setCompoundDrawablePadding(MARGIN + PADDING);
        mTitle.setCompoundDrawablesWithIntrinsicBounds(
                icon, null, null, null);
        mContent.addView(mTitle);
    }

    private void setUpWebView() {
        mWebView = new NoNPEWebView(getContext());
        
        //XXX Rotating to landscape on login screen put login button out of sight without ability to scroll to it. 
        // So scrollbars were enabled here. -Lance
        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mWebView.setVerticalScrollBarEnabled(true);
        mWebView.setHorizontalScrollBarEnabled(true);

        mWebView.setWebViewClient(new FbDialog.FbWebViewClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.loadUrl(mUrl);
        mWebView.setLayoutParams(FILL);
        mContent.addView(mWebView);
    }

	//WebView wasn't finishing loading after the dialog had been dismissed
	//in some situations. So make sure it is stopped and doesn't callback. -Lance
    @Override
	protected void onStop() {
		super.onStop();
		if ( null != mWebView ) {
			mWebView.setWebViewClient(new WebViewClient());
			mWebView.stopLoading();
		}
		DialogUtil.safeDismiss(mSpinner);
	}	    
    
    private class FbWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d("Facebook-WebView", "Redirect URL: " + url);
            
            // WebView isn't showing anything on the stream.publish page for some reason. So show in real browser.
            /*
            if ( url.contains("stream.publish") ) {
                getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                DialogUtil.safeDismiss(FbDialog.this);
                return true;
            }
             */            
            
            if (url.startsWith(Facebook.REDIRECT_URI)) {
                Bundle values = Util.parseUrl(url);

                String error = values.getString("error");
                if (error == null) {
                    error = values.getString("error_type");
                }

                if (error == null) {
                    mListener.onComplete(values);
                } else if (error.equals("access_denied") ||
                           error.equals("OAuthAccessDeniedException")) {
                    mListener.onCancel();
                } else {
                    mListener.onFacebookError(new FacebookError(error));
                }
                DialogUtil.safeDismiss(FbDialog.this);
                return true;
            } else if (url.startsWith(Facebook.CANCEL_URI)) {
                mListener.onCancel();
                DialogUtil.safeDismiss(FbDialog.this);
                return true;
            } else if (url.contains(DISPLAY_STRING)) {
                return false;
            }
            // launch non-dialog URLs in a full browser
            getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            mListener.onError(
                    new DialogError(description, errorCode, failingUrl));
            DialogUtil.safeDismiss(FbDialog.this);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d("Facebook-WebView", "Webview loading URL: " + url);
                            
            //url = url.replaceAll("display=touch", "display=wap");
            
            //Log.d("Facebook-WebView", "Webview loading URL: " + url);
            
            /*
            // WebView isn't showing anything on the stream.publish page for some reason. So show in real browser.
            if ( url.contains("stream.publish") ) {
                getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                DialogUtil.safeDismiss(FbDialog.this);
                return;
            }
            */
            
            super.onPageStarted(view, url, favicon);
            DialogUtil.safeShow(mSpinner);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String title = mWebView.getTitle();
            if (title != null && title.length() > 0) {
                mTitle.setText(title);
            }
            DialogUtil.safeDismiss(mSpinner);
        }

    }
}
