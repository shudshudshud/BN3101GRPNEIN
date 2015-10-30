package com.example.asus.anxietytrackerapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;


public class SensorReadingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_readings);



    }

    private void startGraphActivity(Class<? extends Activity> activity) {
        Intent intent = new Intent(SensorReadingsActivity.this, activity);
        intent.putExtra("type", "line");
        startActivity(intent);
    }
}
