package com.example.proctoring_app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

public class PrivacyFilterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_filter);

        // Add a privacy filter view
        View privacyFilterView = new View(this);
        privacyFilterView.setBackgroundColor(Color.argb(150, 0, 0, 0)); // Semi-transparent black
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((ViewGroup) getWindow().getDecorView()).addView(privacyFilterView, params);
    }
}