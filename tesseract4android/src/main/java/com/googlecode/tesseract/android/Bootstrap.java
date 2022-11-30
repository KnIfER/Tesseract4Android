package com.googlecode.tesseract.android;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.googlecode.tesseraction.Utils;

public class Bootstrap extends Activity {
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Utils.contentResolver = getApplicationContext().getContentResolver();
		finish();
	}
}
