package com.liskovsoft.smartyoutubetv.bootstrap.dialogtweaks.items;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.liskovsoft.sharedutils.dialogs.GenericSelectorDialog.DialogSourceBase.DialogItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpHelpers;
import com.liskovsoft.smartyoutubetv.R;
import com.liskovsoft.smartyoutubetv.misc.ProxyManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebProxyDialogItem extends DialogItem {
    public static final String TAG = WebProxyDialogItem.class.getSimpleName();
    private final Context mContext;
    private AlertDialog mProxyConfigDialog;
    private final ProxyManager mProxyManager;
    private final Handler mProxyTestHandler;
    private final ArrayList<Call> mUrlTests;
    private int mNumTests;

    public WebProxyDialogItem(Context context) {
        super(context.getString(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? R.string.enable_web_proxy : R.string.proxy_not_supported), false);

        mContext = context;
        mProxyManager = new ProxyManager(mContext);
        mProxyTestHandler = new Handler(Looper.myLooper());
        mUrlTests = new ArrayList<>();
    }

    @Override
    public boolean getChecked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return mProxyManager.isProxyEnabled();
        }
        else {
            return false;
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mProxyManager.saveProxyInfoToPrefs(null, checked);
            if (checked)
                // FIXME: If user hit cancel in dialog, proxy remains enabled.
                showProxyConfigDialog();
        }
    }

    protected void appendStatusMessage(String msgFormat, Object ...args) {
        TextView statusView = mProxyConfigDialog.findViewById(R.id.proxy_config_message);
        String message = String.format(msgFormat, args);
        if (statusView.getText().toString().isEmpty())
            statusView.append(message);
        else
            statusView.append("\n"+message);
    }

    protected void appendStatusMessage(int resId, Object ...args) {
        appendStatusMessage(mContext.getString(resId), args);
    }

    protected Proxy validateProxyConfigFields() {
        boolean isConfigValid = true;
        int proxyTypeId = ((RadioGroup) mProxyConfigDialog.findViewById(R.id.proxy_type)).getCheckedRadioButtonId();
        if (proxyTypeId == -1) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_type_invalid);
            mProxyConfigDialog.findViewById(R.id.proxy_type_http).requestFocus();
        }
        String proxyHost = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_host)).getText().toString();
        if (proxyHost.isEmpty()) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_host_invalid);
        }
        String proxyPortString = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_port)).getText().toString();
        int proxyPort = proxyPortString.isEmpty() ? 0 : Integer.parseInt(proxyPortString);
        if (proxyPort <= 0) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_port_invalid);
        }
        if (!isConfigValid) {
            return null;
        }
        Proxy.Type proxyType = proxyTypeId == R.id.proxy_type_http ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        return new Proxy(proxyType, InetSocketAddress.createUnresolved(proxyHost, proxyPort));
    }

    protected void testProxyConnections() {
        Proxy proxy = validateProxyConfigFields();
        if (proxy == null) {
            appendStatusMessage(R.string.proxy_test_aborted);
            return;
        }

        String[] testUrls = mContext.getString(R.string.proxy_test_urls).split("\n");
        OkHttpClient okHttpClient = OkHttpHelpers.createOkHttpClient();

        for (String urlString: testUrls) {
            int serialNo = ++ mNumTests;
            Request request = new Request.Builder().url(urlString).build();
            appendStatusMessage(R.string.proxy_test_start, serialNo, urlString);
            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled())
                        mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_cancelled, serialNo));
                    else
                        mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_error, serialNo, e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String protocol = response.protocol().toString().toUpperCase();
                    int code = response.code();
                    String status = response.message();
                    mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_status,
                            serialNo, protocol, code, status.isEmpty() ? "OK" : status));
                }
            });
            mUrlTests.add(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected void showProxyConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View contentView = inflater.inflate(R.layout.web_proxy_config, null);

        if (mProxyManager.getProxyType() == Proxy.Type.DIRECT) {
            ((EditText) contentView.findViewById(R.id.proxy_host)).setText("");
            ((EditText) contentView.findViewById(R.id.proxy_port)).setText("");
            ((RadioGroup) contentView.findViewById(R.id.proxy_type)).clearCheck();
        }
        else {
            ((EditText) contentView.findViewById(R.id.proxy_host)).setText(mProxyManager.getProxyHost());
            ((EditText) contentView.findViewById(R.id.proxy_port)).setText(String.valueOf(mProxyManager.getProxyPort()));
            int proxyTypeId = mProxyManager.getProxyType() == Proxy.Type.HTTP ? R.id.proxy_type_http : R.id.proxy_type_socks;
            ((RadioGroup) contentView.findViewById(R.id.proxy_type)).check(proxyTypeId);
        }

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        mProxyConfigDialog = builder
                .setTitle(R.string.proxy_settings_title)
                .setView(contentView)
                .setNeutralButton(R.string.proxy_test_btn, (dialog, which) -> { })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> { })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> { })
                .create();

        mNumTests = 0;
        mProxyConfigDialog.show();

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
            ((TextView) mProxyConfigDialog.findViewById(R.id.proxy_config_message)).setText("");
            Proxy proxy = validateProxyConfigFields();
            if (proxy == null) {
                appendStatusMessage("Please correct proxy settings first.");
            }
            else {
                Log.d(TAG, "Saving proxy info: " + proxy);
                mProxyManager.saveProxyInfoToPrefs(proxy, true);
                for (Call call: mUrlTests) call.cancel();
                mProxyConfigDialog.dismiss();
            }
        });

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((view) -> {
            for (Call call: mUrlTests) call.cancel();
            mUrlTests.clear();
            ((TextView) mProxyConfigDialog.findViewById(R.id.proxy_config_message)).setText("");
            testProxyConnections();
        });

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((view) -> {
            for (Call call: mUrlTests) call.cancel();
            mProxyConfigDialog.dismiss();
        });
    }
}
