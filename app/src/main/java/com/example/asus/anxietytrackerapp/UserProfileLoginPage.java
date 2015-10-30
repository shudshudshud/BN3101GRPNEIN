package com.example.asus.anxietytrackerapp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;



public class UserProfileLoginPage extends Activity implements OnItemSelectedListener {

    SharedPreferences sharedpreferences;
    private DatePicker datePicker;
    private Calendar calendar;
    private TextView dateView;
    private int year, month, day;

    TextView selectedFitness;
    TextView selectedGender;

    EditText myName;
    public static final String MyPREFERENCES = "MyPrefs";   //preference name
    public static final String Name = "nameKey";
    public static final String Date = "dateKey";
    public static final String Gender = "genderKey";
    public static final String Fitness = "fitnessKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile_login_page);

        myName = (EditText) findViewById(R.id.editText);

        sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
        if (sharedpreferences.contains(Name)) {
            myName.setText(sharedpreferences.getString(Name, ""));
        }
        if (sharedpreferences.contains(Gender)) {
            selectedGender.setText(sharedpreferences.getString(Gender, ""));

        }
        if (sharedpreferences.contains(Date)) {
            dateView.setText(sharedpreferences.getString(Date, ""));

        }if (sharedpreferences.contains(Fitness)) {
            selectedFitness.setText(sharedpreferences.getString(Fitness, ""));

        }


        //Spinner element to display gender
        final Spinner spinner1 = (Spinner) findViewById(R.id.Gender);
        selectedGender = (TextView) findViewById(R.id.textView2);
        //Spinner Dropdown elements
        ArrayList<String> categories1 = new ArrayList<>();
        categories1.add("Male");
        categories1.add("Female");
        //Creating adapter for spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories1);
        //Dropdown layout style
        adapter.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));
        //Attaching data adapter to spinner
        spinner1.setAdapter(adapter);
        //Spinner click listener & To save the selected item of a spinner in SharedPreferences.
        spinner1.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item1 = spinner1.getSelectedItem().toString();   //get selected spinner value and put to string
                selectedGender.setText(item1);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setCurrentDate();

        //Spinner element
        final Spinner spinner2 = (Spinner) findViewById(R.id.PhysicalActivityLevel);
        selectedFitness = (TextView) findViewById(R.id.textView12);
        //Spinner click listener
        spinner2.setOnItemSelectedListener(this);
        //Spinner Dropdown elements
        ArrayList<String> categories2 = new ArrayList<>();
        categories2.add("Extremely Inactive");
        categories2.add("Sedentary");
        categories2.add("Moderately Active");
        categories2.add("Vigorously Active");
        categories2.add("Extremely Active");
        //Creating adapter for spinner
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories2);
        //Dropdown layout style
        adapter2.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));
        //Attaching data adapter to spinner
        spinner2.setAdapter(adapter2);
        //Spinner click listener & To save the selected item of a spinner in SharedPreferences
        spinner2.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item2 = spinner2.getSelectedItem().toString();
                selectedFitness.setText(item2);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    public void saveProfile(View v) {

        //To save user's profile data into phone's internal storage.
        String name  = myName.getText().toString();
        String dob  = dateView.getText().toString();
        String gender  = selectedGender.getText().toString();
        String fitness  = selectedFitness.getText().toString();


        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString(Name, name);
        editor.putString(Date, dob);
        editor.putString(Gender, gender);
        editor.putString(Fitness, fitness);
        editor.commit();
    }

    public void getProfile (View view) {
        myName = (EditText) findViewById(R.id.editText);
        dateView = (TextView) findViewById(R.id.textView4);
        selectedFitness = (TextView) findViewById(R.id.textView12);
        selectedGender = (TextView) findViewById(R.id.textView2);

        sharedpreferences = getSharedPreferences(MyPREFERENCES,
                Context.MODE_PRIVATE);

        if (sharedpreferences.contains(Name)) {
            myName.setText(sharedpreferences.getString(Name, ""));
        }
        if (sharedpreferences.contains(Gender)) {
            selectedGender.setText(sharedpreferences.getString(Gender, ""));

        }
        if (sharedpreferences.contains(Date)) {
            dateView.setText(sharedpreferences.getString(Date, ""));

        }if (sharedpreferences.contains(Fitness)) {
            selectedFitness.setText(sharedpreferences.getString(Fitness, ""));

        }
    }



    private void setCurrentDate() {

        dateView = (TextView) findViewById(R.id.textView4);
        datePicker = (DatePicker) findViewById(R.id.datePicker);

        final Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        day = c.get(Calendar.DAY_OF_MONTH);

        // set current date into textview
        dateView.setText(new StringBuilder()
                // Month is 0 based, just add 1
                .append(month + 1).append("-").append(day).append("-")
                .append(year).append(" "));

        // set current date into datepicker
        datePicker.init(year, month, day, null);

    }


    public void setDate(View view) {

        showDialog(999);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == 999) {
            return new DatePickerDialog(UserProfileLoginPage.this, myDateListener, year, month, day);
        }
        return null;
    }

    private DatePickerDialog.OnDateSetListener myDateListener = new DatePickerDialog.OnDateSetListener() {

        public void onDateSet(DatePicker view, int mBirthYear, int mMonthOfYear, int mDayOfMonth) {
            year = mBirthYear;
            month = mMonthOfYear;
            day = mDayOfMonth;

            //set selected date into textview
            dateView.setText(new StringBuilder().append(day).append("/")
                    .append(month).append("/").append(year));

            //set selected date into datepicker
            datePicker.init(year, month, day, null);
        }
    };


    public void mainPage(View v){
        Intent intent = new Intent(UserProfileLoginPage.this,MainMenuPage.class);
        startActivity(intent);
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }


}







