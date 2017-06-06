/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 26/01/2017.
 */

package cm.aptoide.pt.v8engine.billing.view;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.V8Engine;
import com.jakewharton.rxrelay.PublishRelay;
import rx.Observable;

public class WebAuthorizationActivity extends ProductActivity
    implements WebAuthorizationView {

  private WebView webView;
  private View progressBarContainer;
  private AlertDialog unknownErrorDialog;
  private PublishRelay<Void> mainUrlSubject;
  private PublishRelay<Void> redirectUrlSubject;
  private PublishRelay<Void> backButtonSelectionSubject;
  private ClickHandler clickHandler;

  @SuppressLint("SetJavaScriptEnabled") @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_web_authorization);

    webView = (WebView) findViewById(R.id.activity_boa_compra_authorization_web_view);
    webView.getSettings()
        .setJavaScriptEnabled(true);
    webView.setWebChromeClient(new WebChromeClient());
    progressBarContainer = findViewById(R.id.activity_web_authorization_preogress_bar);
    unknownErrorDialog = new AlertDialog.Builder(this).setMessage(R.string.having_some_trouble)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          finish();
        })
        .create();
    mainUrlSubject = PublishRelay.create();
    redirectUrlSubject = PublishRelay.create();
    backButtonSelectionSubject = PublishRelay.create();
    clickHandler = () -> {
      backButtonSelectionSubject.call(null);
      return false;
    };
    registerBackClickHandler(clickHandler);

    attachPresenter(new WebAuthorizationPresenter(this,
        ((V8Engine) getApplicationContext()).getAptoideBilling(),
        getIntent().getIntExtra(EXTRA_PAYMENT_ID, 0),
        ((V8Engine) getApplicationContext()).getPaymentAnalytics(),
        ((V8Engine) getApplicationContext()).getPaymentSyncScheduler(),
        ProductProvider.fromIntent(((V8Engine) getApplicationContext()).getAptoideBilling(),
            getIntent())), savedInstanceState);
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    ((ViewGroup) webView.getParent()).removeView(webView);
    webView.setWebViewClient(null);
    webView.destroy();
    unknownErrorDialog.dismiss();
    unregisterBackClickHandler(clickHandler);
  }

  @Override public void showLoading() {
    progressBarContainer.setVisibility(View.VISIBLE);
  }

  @Override public void hideLoading() {
    progressBarContainer.setVisibility(View.GONE);
  }

  @Override public void showUrl(String mainUrl, String redirectUrl) {
    webView.setWebViewClient(new WebViewClient() {

      @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (url.equals(redirectUrl)) {
          redirectUrlSubject.call(null);
        }
      }

      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (url.equals(mainUrl)) {
          mainUrlSubject.call(null);
        }
      }
    });
    webView.loadUrl(mainUrl);
  }

  @Override public Observable<Void> backToStoreSelection() {
    return redirectUrlSubject;
  }

  @Override public Observable<Void> backButtonSelection() {
    return backButtonSelectionSubject;
  }

  @Override public Observable<Void> urlLoad() {
    return mainUrlSubject;
  }

  @Override public void dismiss() {
    finish();
  }

  @Override public void showErrorAndDismiss() {
    unknownErrorDialog.show();
  }
}
