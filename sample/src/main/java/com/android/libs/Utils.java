package com.android.libs;

import android.util.Log;

public class Utils {

	private static final String TAG = "Utils";

	public void i(String message) {
		Log.i(TAG, "i: " + message);
	}

	public void d(String message) {
		Log.d(TAG, "d: " + message);
	}

	public void e(Throwable throwable) {
		Log.e(TAG, "e: ", throwable);
	}
}
