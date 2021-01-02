package com.liskovsoft.smartyoutubetv.misc.appstatewatcher.handlers;

import android.app.Activity;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv.CommonApplication;
import com.liskovsoft.smartyoutubetv.misc.SmartUtils;
import com.liskovsoft.smartyoutubetv.misc.appstatewatcher.AppStateWatcherBase.StateHandler;

public class ForceOldUIHandler extends StateHandler {
    private static final String TAG = ForceOldUIHandler.class.getSimpleName();
    private static final String NEW_UI_COOKIE2 = "VISITOR_INFO1_LIVE=xcc12hbEjFM; path=/; domain=.youtube.com; expires=Sat, 25-Apr-2025 15:42:26 GMT; httponly";
    private static final String NEW_UI_COOKIE = "VISITOR_INFO1_LIVE=cp3UVuEA3l4; path=/; domain=.youtube.com; expires=Sat, 25-Apr-2025 15:42:26 GMT; httponly";
    private static final String OLD_UI_COOKIE = "VISITOR_INFO1_LIVE=ErVksiAQ6pg; path=/; domain=.youtube.com; expires=Sat, 25-Apr-2025 15:42:26 GMT; httponly";
    private static final String COOKIE_URL = "https://www.youtube.com";
    private final Activity mContext;

    public ForceOldUIHandler(Activity context) {
        mContext = context;
    }

    @Override
    public void onInit() {
        if (!CommonApplication.getPreferences().isUserLogged()) {
            Log.d(TAG, "User is not logged. Don't force OldUI...");
            return;
        }

        String cookie = OLD_UI_COOKIE;

        SmartUtils.setSecureCookie(cookie, COOKIE_URL, mContext);
    }
}
