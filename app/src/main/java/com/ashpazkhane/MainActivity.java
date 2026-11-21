package com.ashpazkhane;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

import ir.cafebazaar.poolakey.ConnectionConfiguration;
import ir.cafebazaar.poolakey.Payment;
import ir.cafebazaar.poolakey.PaymentConfiguration;
import ir.cafebazaar.poolakey.config.PurchaseRequest;
import ir.cafebazaar.poolakey.config.SecurityCheck;
import ir.cafebazaar.poolakey.entity.PurchaseInfo;
import ir.cafebazaar.poolakey.entity.PurchaseState;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Payment payment;
    private boolean isConnected = false;

    private static final String RSA_PUBLIC_KEY = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwD..."; 
    private static final String PRODUCT_YEARLY = "yearly_sub";
    private static final String PRODUCT_LIFETIME = "lifetime_sub";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        initPoolakey();
    }

    private void initPoolakey() {
        try {
            SecurityCheck securityCheck;
            if (RSA_PUBLIC_KEY.contains("MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwD...")) {
                securityCheck = SecurityCheck.Disable.INSTANCE;
            } else {
                securityCheck = new SecurityCheck.Enable(RSA_PUBLIC_KEY);
            }

            ConnectionConfiguration connection = new ConnectionConfiguration();
            payment = new PaymentConfiguration(securityCheck, connection).createPayment(this);

            payment.connect().subscribe(
                () -> {
                    isConnected = true;
                    checkExistingPurchases();
                },
                throwable -> Log.e("Poolakey", "Connect failed")
            );
        } catch (Exception e) {
            Log.e("Poolakey", "Init error: " + e.getMessage());
        }
    }

    private void checkExistingPurchases() {
        if (!isConnected) return;
        payment.getPurchasedProducts().subscribe(
            purchases -> {
                for (PurchaseInfo info : purchases.getPurchasedProducts()) {
                    if (info.getPurchaseState() == PurchaseState.PURCHASED) {
                        String plan = info.getProductId().contains("yearly") ? "yearly" : "lifetime";
                        callJsSuccess(plan);
                    }
                }
            },
            throwable -> Log.e("Poolakey", "Query failed")
        );
    }

    private void callJsSuccess(String planId) {
        String js = "window.onPurchaseSuccess({planId:'" + planId + "'})";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void callJsFailed(String reason) {
        String js = "window.onPurchaseFailed('" + reason + "')";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void purchase(String planId) {
            if (!isConnected || payment == null) {
                callJsFailed("not_connected");
                return;
            }
            String productId = planId.equals("yearly") ? PRODUCT_YEARLY : PRODUCT_LIFETIME;
            String requestId = "req-" + System.currentTimeMillis();

            payment.purchaseProduct(new PurchaseRequest(productId, requestId))
                .subscribe(
                    purchaseInfo -> {
                        if (purchaseInfo.getPurchaseState() == PurchaseState.PURCHASED) {
                            callJsSuccess(planId);
                        } else {
                            callJsFailed("not_purchased");
                        }
                    },
                    throwable -> callJsFailed("cancelled")
                );
        }
    }
                          }
