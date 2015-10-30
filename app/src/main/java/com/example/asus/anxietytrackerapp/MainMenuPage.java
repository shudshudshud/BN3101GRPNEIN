package com.example.asus.anxietytrackerapp;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;


public class MainMenuPage extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu_page);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void feedbackControl(View v){
        Intent intent1 = new Intent(MainMenuPage.this, FeedbackControl.class);
        startActivity(intent1);
    }

    public void sensorData (View v){
        //Intent intent3 = new Intent(MainMenuPage.this,SensorReadingsActivity.class);
        Intent intent3 = new Intent(MainMenuPage.this, RealGraphActivity.class);
        startActivity(intent3);
    }


    public void goalTracker (View v){
        Intent intent4 = new Intent(MainMenuPage.this,Goals.class);
        startActivity(intent4);
    }

    public void anxietyDiary (View v){
        Intent intent4 = new Intent(MainMenuPage.this,DailyAnxietyDiary.class);
        startActivity(intent4);
    }



}


