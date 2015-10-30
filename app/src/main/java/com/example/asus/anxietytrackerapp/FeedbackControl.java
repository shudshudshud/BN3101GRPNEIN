package com.example.asus.anxietytrackerapp;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.media.MediaPlayer;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;

public class FeedbackControl extends Activity {

    MediaPlayer musicplayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_control);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        //To create a list showing doctor's reminders to reduce anxiety.
        ListView listView = (ListView)findViewById(R.id.remindersList);


        //Create music player to play calm music
        Button b7 = (Button)findViewById(R.id.button7);
        Button b8 = (Button)findViewById(R.id.button8);
        b7.setOnClickListener(new View.OnClickListener(){
           @Override
           public void onClick(View v){
               musicplayer = MediaPlayer.create(FeedbackControl.this, R.raw.calmmusic);
               musicplayer.start();
           }});
        b8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicplayer.pause();
            }
        });
    }

}
