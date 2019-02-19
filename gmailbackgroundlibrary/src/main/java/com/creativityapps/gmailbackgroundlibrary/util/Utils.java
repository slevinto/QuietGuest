package com.creativityapps.gmailbackgroundlibrary.util;

import android.content.Context;
import android.net.ConnectivityManager;

public class Utils {
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager mgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if ( mgr != null ) {
            return (mgr.getActiveNetworkInfo() != null);
        }
        else return false;
    }
}
