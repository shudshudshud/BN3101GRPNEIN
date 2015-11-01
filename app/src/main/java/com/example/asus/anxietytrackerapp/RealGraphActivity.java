package com.example.asus.anxietytrackerapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

public class RealGraphActivity extends Activity {


    /*
        SETTIMGS! CHANGE HERE!

    */

    //number of points to display on the graph
    int numPointsToDisplay = 20;

    //threshold for double = zero equality, DO NOT TOUCH unless you know what you are doing
    double doubleZeroThreshold = 0.05;

    //default GSR Calibration values
    double defaultGSRMin = 0;
    double defaultGSRMax = 6;
    double defaultGSR33 = 2;
    double defaultGSR66 = 4;

    //actual GSR values
    double gsrMin = 0;
    double gsrMax = 6;
    double gsr33 = 2;
    double gsr66 = 4;

    //GSR Cutoff for good/bad values
    double gsrCutoff = 0.6;
    //IBI Cutoff for good/bad values
    double ibiCutoff = 0.1;

    double percentageCutoff = 20.0;
    //double sdnnCutoff = 0.0;

    //default SDNN Calibration values
    double defaultSDNNMin = 0;
    double defaultSDNNMax = 6;
    double defaultSDNN33 = 2;
    double defaultSDNN66 = 4;

    //actual SDNN values
    double SDNNMin = 0;
    double SDNNMax = 6;
    double SDNN33 = 2;
    double SDNN66 = 4;

    long nextComputeTime;
    long msecBetweenComputes = 5000;

    public enum CALIBRATION_STATE {
        BEFORE_CALIB, DURING_BASELINE, BREAK_BEFORE_TEST, DURING_TEST, BREAK_BEFORE_MAX, DURING_MAX, AFTER_MAX
    }

    boolean calibrationHappening = false;
    CALIBRATION_STATE currentCalibrationState = CALIBRATION_STATE.BEFORE_CALIB;


    ArrayList<Packet> baselinePackets = new ArrayList<>();
    ArrayList<Packet> testPackets = new ArrayList<>();
    ArrayList<Packet> maxPackets = new ArrayList<>();

    //in MILISECONDS! Minimum duration of tests\
    /* TEST VERSION */

    double minimumBaselineDuration = 1000;
    double minimumTestDuration = 1000;
    double minimumMaxDuration = 1000;
    double sdnnAverageTimeInterval = 5000;

    /* REAL VERSION
    double minimumBaselineDuration = 120000;
    double minimumTestDuration = 120000;
    double minimumMaxDuration = 120000;

    double sdnnAverageTimeInterval = 60000;
    */
    /*
        END OF SETTINGS
     */

    //private final Handler mHandler = new Handler();

    //whatever is needed for data extraction
    static ArrayList<Packet> dataPackets = new ArrayList<Packet>();


    //to plot realtime graph
    private Runnable mTimer1;  //GSR = 1


    private GraphView stressGraph;
    LineGraphSeries<DataPoint> stressSeries = new LineGraphSeries<>();

    private GraphView gsrGraph;
    LineGraphSeries<DataPoint> gsrSeries = new LineGraphSeries<>();

    private GraphView ibiGraph;
    LineGraphSeries<DataPoint> ibiSeries = new LineGraphSeries<>();
    //private ArrayList<DataPoint> seriesGSR;


    //DATA READINGS VARS
    boolean firstData = false;



    //User Interface Variables
    //COMMENTED THIS OUT - DEFINED ONCE ABOVE
    private Handler mHandler;
    final int handlerState = 0;

    //Data Output
    private BluetoothAdapter btAdapter = null;
    //to create connection to the hc-05 module
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    TextView outText;

    private ConnectedThread mConnectedThread;


    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //specify the MAC address of the Bluetooth address that you want to connect to
    //private static String address = "98:D3:31:70:4D:02";
    private static String address = "98:D3:31:70:4C:C7";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_readings);
        //setContentView(R.layout.activity_feedback_control);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        //Toast.makeText(this, "onCreate, ReadGraph..", Toast.LENGTH_LONG).show();
        //seriesGSR = new ArrayList<DataPoint>();


        //Graph setup - Stress
        stressGraph = (GraphView)findViewById(R.id.graphStress);
        stressGraph.getViewport().setXAxisBoundsManual(false);
        stressGraph.getViewport().setYAxisBoundsManual(false);
        stressGraph.addSeries(stressSeries);

        //Graph setup - GSR
        gsrGraph = (GraphView)findViewById(R.id.graph);
        gsrGraph.getViewport().setXAxisBoundsManual(false);
        gsrGraph.getViewport().setYAxisBoundsManual(false);
        gsrGraph.addSeries(gsrSeries);

        //Graph setup - IBI
        ibiGraph = (GraphView)findViewById(R.id.graph2);
        ibiGraph.getViewport().setXAxisBoundsManual(false);
        ibiGraph.getViewport().setYAxisBoundsManual(false);
        ibiGraph.addSeries(ibiSeries);

        outText = ((TextView)findViewById(R.id.dataText));

        //calibration button click listener
        final Button calibrationButton = (Button) findViewById(R.id.buttonCalibrate);
        calibrationButton.setText(currentCalibrationState.toString());
        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //increase the current calibration state
                if (currentCalibrationState != CALIBRATION_STATE.AFTER_MAX) {
                    //change calibration state
                    if (currentCalibrationState != CALIBRATION_STATE.DURING_MAX) {
                        calibrationHappening = true;
                    } else {
                        Toast.makeText(getApplicationContext(), "Calibration Over!", Toast.LENGTH_LONG).show();
                        calibrationHappening = false;

                        //PROCESS CALIBRATION DATA
                        updateCalibrationValues();

                        //CLEAR VARIABLES!
                        dataPackets.clear();
                        //set the next calculation time
                        nextComputeTime = Calendar.getInstance().getTimeInMillis() + msecBetweenComputes;

                    }
                    currentCalibrationState = CALIBRATION_STATE.values()[currentCalibrationState.ordinal() + 1];
                    calibrationButton.setText(currentCalibrationState.toString());
                } else {

                    //currentCalibrationState = CALIBRATION_STATE.values()[0];
                    //calibrationButton.setText(currentCalibrationState.toString());
                }
            }
        });

        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calibrationButton.setEnabled(!calibrationButton.isEnabled());
                currentCalibrationState = CALIBRATION_STATE.values()[0];
                calibrationButton.setText(currentCalibrationState.toString());

                if (!calibrationButton.isEnabled()) {
                    calibrationHappening = false;

                    baselinePackets.clear();
                    testPackets.clear();
                    maxPackets.clear();

                }

            }
        });

        final Button defaultCalib = (Button) findViewById(R.id.buttonCalibrate);
        defaultCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gsrMin  = defaultGSRMin;
                gsrMax  = defaultGSRMax;
                gsr33   = defaultGSR33 ;
                gsr66   = defaultGSR66 ;
                SDNNMin = defaultSDNNMin;
                SDNNMax = defaultSDNNMax;
                SDNN33  = defaultSDNN33 ;
                SDNN66  = defaultSDNN66 ;


            }
        });


//Thread handlers are implemented in the main thread of an application and are primarily used to make updates to the user interface in response
// to messages sent by another thread running within the application’s process.

        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == handlerState) {       //if message is what we want
                    //Toast.makeText(getApplicationContext(), "hi", Toast.LENGTH_LONG).show();

                    // Gets the string message from the incoming Message object.
                    String readMessage = (String) msg.obj;

                    //append the message to the recDataString - overall data
                    recDataString.append(readMessage);

                    //check if we have >= 1 packet inside
                    if (hasAtLeastOnePacket(recDataString.toString())) {
                        //outText.setText(recDataString);
                        dataExtractor(recDataString.toString());
                    }




                    //StringBuilder sensordata = recDataString;             //get sensor value from string
                    //sensorView.setText(P.getGSR);    //update the textviews with sensor values
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();


        //btnRead.setOnClickListener(new View.OnClickListener() {
        //public void onClick(View v) {
        // mConnectedThread.write("1");    // Send "1" via Bluetooth
        // Toast.makeText(getBaseContext(), "Receiving data", Toast.LENGTH_SHORT).show();
        // }
        // });
    }

    //if data is .....#, remove the ..... part
    public String trimStart(String data) {
        //remove everything before first #
        return data.substring(data.indexOf("#"));
    }

    public double getAverageGSR(ArrayList<Packet> packets) {
        double gsrAccumulator = 0;
        double gsrCount = 0;

        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                gsrAccumulator += packet.getGSR();
                gsrCount++;
            }
        }

        double averageGSR = gsrAccumulator / gsrCount;
        return averageGSR;
    }



    public double getSDGSR(ArrayList<Packet> packets) {
        double gsrAccumulator = 0;
        double gsrCount = 0;

        //getting the mean and number of useful packets
        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                gsrAccumulator += packet.getGSR();
                gsrCount++;
            }
        }

        //average calculated
        double averageGSR = gsrAccumulator / gsrCount;
        

        //all (xi - xavg) ^ 2 - calculate squared sum
        double sumSquared = 0;
        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                sumSquared += Math.pow((packet.getGSR() - averageGSR), 2);
            }
        }

        //final SD value
        double SD = Math.sqrt((1 / gsrCount) * sumSquared);
        Log.d("SDNN", "GSR SD: " + SD);
        
        return SD;
    }

    public double getSDSDNN(ArrayList<Double> sdnns) {

        double avgSDNN = averager(sdnns);
        

        //all (xi - xavg) ^ 2 - calculate squared sum
        double sumSquared = 0;
        for (Double sdnn : sdnns) {
                sumSquared += Math.pow((sdnn - avgSDNN), 2);
        }

        //final SD value
        double SDSDNN = Math.sqrt((1 / sdnns.size()) * sumSquared);
        Log.d("SDSDNN", "SDNNSD: " + SDSDNN);

        return SDSDNN;
    }

    public double getSDNN(ArrayList<Packet> packets) {
        double IBIAccumulator = 0;
        double IBICount = 0;

        //getting the mean and number of useful packets
        for (Packet packet : packets) {
            if (packet.getIBI() > ibiCutoff) {
                IBIAccumulator += packet.getIBI();
                IBICount++;
            }
        }

        //average calculated
        double averageIBI = IBIAccumulator / IBICount;


        //all (xi - xavg) ^ 2 - calculate squared sum
        double sumSquared = 0;
        for (Packet packet : packets) {
            if (packet.getIBI() > ibiCutoff) {
                sumSquared += Math.pow((packet.getIBI() - averageIBI), 2);
            }
        }

        //final SD value
        double SD = Math.sqrt((1 / IBICount) * sumSquared);
        Log.d("SDNN", "IBI SD = SDNN: " + SD);

        return SD;
    }

    public double getMinGSR(ArrayList<Packet> packets) {
        double gsrMin = Double.MAX_VALUE;

        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                double gsrVal = packet.getGSR();

                if (gsrVal < gsrMin) {
                    gsrMin = gsrVal;
                }
            }
        }

        return gsrMin;
    }

    public double getMaxGSR(ArrayList<Packet> packets) {
        double gsrMax = -Double.MAX_VALUE;

        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                double gsrVal = packet.getGSR();

                if (gsrVal > gsrMax) {
                    gsrMax = gsrVal;
                }
            }
        }

        return gsrMax;
    }

    public void updateCalibrationValues() {
        //have 3 arraylists, baselinePackets, testPackets and maxPackets

        // ---- GSR SECTION ----

        //AVERAGES
        double baselineAvg = getAverageGSR(baselinePackets);
        double testAvg = getAverageGSR(testPackets);
        double maxAvg = getAverageGSR(maxPackets);

        //STANDARD DEVIATIONS
        double baselineSD = getSDGSR(baselinePackets);
        double testSD = getSDGSR(testPackets);
        double maxSD = getSDGSR(maxPackets);

        ArrayList<Packet> overallCalibrationPackets = new ArrayList<>();
        overallCalibrationPackets.addAll(baselinePackets);
        overallCalibrationPackets.addAll(testPackets);
        overallCalibrationPackets.addAll(maxPackets);

        double overallGSRMin = getMinGSR(overallCalibrationPackets);
        double overallGSRMax = getMaxGSR(overallCalibrationPackets);

        //95% CONFIDENCE INTERVAL BOUNDS
        //L = lower, H = higher
        //M = mean
        //S = start, T = test, M = max
        //pick and choose to figure out name
        double LMS = (1.96 * baselineSD) - baselineAvg;
        double HMS = (1.96 * baselineSD) + baselineAvg;
        double LMT = (1.96 * testSD) - testAvg;
        double HMT = (1.96 * testSD) + testAvg;
        double LMM = (1.96 * maxSD) - maxAvg;
        double HMM = (1.96 * maxSD) + maxAvg;



        Log.d("BEFORE", "BEFORE!");
        Log.d("gsrMin", gsrMin + "");
        Log.d("gsr33", gsr33 + "");
        Log.d("gsr66", gsr66 + "");
        Log.d("gsrMax", gsrMax + "");


        //set global calib parameters
        gsrMin = overallGSRMin;
        gsrMax = overallGSRMax;



        //based on syad's rules: setting calib values
        //sorry if this is incomprehensible, had good reason.
        if (LMT > HMS) {
            gsr33 = LMT;
        } else if (LMT < HMS && LMT > baselineAvg) {
            gsr33 = (LMT + HMS) / 2;
        } else if (LMT < baselineAvg) {
            gsr33 = testAvg;
        } else {
            Log.d("gsr33 problem", "edge case for gsr33");
        }

        if (LMM >= HMT) {
            gsr66 = LMM;
        } else if (LMM <= HMT && LMM >= testAvg) {
            gsr66 = (LMM + HMT) / 2;
        } else if (LMM <= testAvg) {
            gsr66 = maxAvg;
        } else {
            Log.d("gsr66 problem", "edge case for gsr66");
        }

        Log.d("AFTER", "AFTER!");
        Log.d("gsrMin", gsrMin + "");
        Log.d("gsr33", gsr33 + "");
        Log.d("gsr66", gsr66 + "");
        Log.d("gsrMax", gsrMax + "");

        // ---- END GSR SECTION ----


        // ---- START IBI TO SDNN CONVERSION SECTION ----
        //initialise the basic sdnn arrays
        ArrayList<Double> baselineSDNN = new ArrayList<>();
        ArrayList<Double> testSDNN = new ArrayList<>();
        ArrayList<Double> maxSDNN = new ArrayList<>();
        
        
       
        /*
        //get an array of baseline AND test packets to moving average over
        ArrayList<Packet> baselineAndTestPackets = new ArrayList<>();
        baselineAndTestPackets.addAll(baselinePackets);
        baselineAndTestPackets.addAll(testPackets);

        ArrayList<Packet> testAndMaxPackets = new ArrayList<>();
        baselineAndTestPackets.addAll(testPackets);
        baselineAndTestPackets.addAll(maxPackets);
        */
        
        //Need to convert IBI Values to moving average SDNN arrays
        //check for correct durations!
        if ((getPacketListDuration(baselinePackets) > minimumBaselineDuration) ||
                (getPacketListDuration(testPackets) > minimumTestDuration) ||
                (getPacketListDuration(maxPackets) > minimumMaxDuration)) {


            //get the baseline SDNN values
            for (int i = 0; i < baselinePackets.size(); i++) {
                //get moving 60s window of data from packets
                ArrayList<Packet> currentWindow = getXMsecDataFromIndex(baselinePackets, sdnnAverageTimeInterval, i);
                if (currentWindow.size() != 0) {
                    //if we didn't get returned zero, means we are still within the bounds of the array
                    baselineSDNN.add(getSDNN(currentWindow));
                } else {
                    break;
                }

            }
            //get the test SDNN values
            for (int i = 0; i < testPackets.size(); i++) {
                //get moving 60s window of data from packets
                ArrayList<Packet> currentWindow = getXMsecDataFromIndex(testPackets, sdnnAverageTimeInterval, i);
                if (currentWindow.size() != 0) {
                    //if we didn't get returned zero, means we are still within the bounds of the array
                    testSDNN.add(getSDNN(currentWindow));
                } else {
                    break;
                }

            }

            //get the max SDNN values
            for (int i = 0; i < maxPackets.size(); i++) {
                //get moving 60s window of data from packets
                ArrayList<Packet> currentWindow = getXMsecDataFromIndex(maxPackets, sdnnAverageTimeInterval, i);
                if (currentWindow.size() != 0) {
                    //if we didn't get returned zero, means we are still within the bounds of the array
                    maxSDNN.add(getSDNN(currentWindow));
                } else {
                    break;
                }

            }

            // ---- END IBI TO SDNN CONVERSION SECTION ----
            

            
        } else {
            Log.e("min durations", "Minimum durations not followed for calib!");
            throw new IllegalArgumentException("min durations incorrect");
        }

        Log.d("sdnn lengths", "base sdnn: " + baselineSDNN.size()  + "test sdnn: " + maxSDNN.size() +  "base sdnn: " + maxSDNN.size());

        // ---- START SDNN CALIBRATION SECTION ----        
        double baselineSDNNAverage = averager(baselineSDNN);
        double testSDNNAverage = averager(testSDNN);
        double maxSDNNAverage = averager(maxSDNN);

        Log.d("overallsddnn", "baseline avg: " + baselineSDNNAverage + " testsdnnaverage: " + testSDNNAverage + " maxSDNNAvg: " + maxSDNNAverage );



        double baselineSDNNSD = getSDSDNN(baselineSDNN);
        double testSDNNSD = getSDSDNN(testSDNN);
        double maxSDNNSD = getSDSDNN(maxSDNN);

        Log.d("overallsddnn", "baseline sdnnsd: " + baselineSDNNSD + " testsdnnasd: " + testSDNNSD + " maxSDNNSd: " + maxSDNNSD );


        ArrayList<Double> overallSDNNValues = new ArrayList<>();
        overallSDNNValues.addAll(baselineSDNN);
        overallSDNNValues.addAll(testSDNN);
        overallSDNNValues.addAll(maxSDNN);


        double overallSDNNMin = Collections.min(overallSDNNValues);
        double overallSDNNMax = Collections.max(overallSDNNValues);

        Log.d("overallsddnn", "overallSDNNValues len: " + overallSDNNValues.size() + " overall sdnnmin: " + overallSDNNMin + " overallsdnnmax: " + overallSDNNMax );
        Log.d("overallsddnn",  "min: " + Collections.min(overallSDNNValues) + " | max: " + Collections.max(overallSDNNValues));


        Log.d("BEFORE", "BEFORE!");
        Log.d("SDNNMin", SDNNMin + "");
        Log.d("SDNN33", SDNN33 + "");
        Log.d("SDNN66", SDNN66 + "");
        Log.d("SDNNMax", SDNNMax + "");


        //NOTE: MIN AND MAX FLIPPED! ON PURPOSE!
        SDNNMin = overallSDNNMax;
        SDNNMax = overallSDNNMin;
        
        //95% CONFIDENCE INTERVAL BOUNDS
        //L = lower, H = higher
        //M = mean
        //S = start, T = test, M = max
        //pick and choose to figure out name
        double LMS_SDNN = (1.96 * baselineSDNNSD) - baselineSDNNAverage;
        double HMS_SDNN = (1.96 * baselineSDNNSD) + baselineSDNNAverage;
        double LMT_SDNN = (1.96 * testSDNNSD) - testSDNNAverage;
        double HMT_SDNN = (1.96 * testSDNNSD) + testSDNNAverage;
        double LMM_SDNN = (1.96 * maxSDNNSD) - maxSDNNAverage;
        double HMM_SDNN = (1.96 * maxSDNNSD) + maxSDNNAverage;

        //based on syad's rules: setting calib values
        //sorry if this is incomprehensible, had good reason.

        if (HMT_SDNN <= LMS_SDNN) {
            SDNN33 = HMT_SDNN;
        } else if (HMT_SDNN >= LMS_SDNN && HMT_SDNN <= baselineSDNNAverage) {
            SDNN33 = (HMT_SDNN + LMS_SDNN) / 2;
        } else if (HMT_SDNN >= baselineSDNNAverage) {
            SDNN33 = testSDNNAverage;
        } else {
            Log.d("SDNN33 problem", "edge case for SDNN33");
        }

        if (HMM_SDNN < LMT_SDNN) {
            SDNN66 = HMM_SDNN;
        } else if (HMM_SDNN > LMT_SDNN && HMT_SDNN < testSDNNAverage) {
            SDNN66 = (HMM_SDNN + LMT_SDNN) / 2;
        } else if (HMM_SDNN > testSDNNAverage) {
            SDNN66 = maxSDNNAverage;
        } else {
            Log.d("SDNN33 problem", "edge case for SDNN66");
        }


        Log.d("AFTER", "AFTER!!");
        Log.d("SDNNMin", SDNNMin + "");
        Log.d("SDNN33", SDNN33 + "");
        Log.d("SDNN66", SDNN66 + "");
        Log.d("SDNNMax", SDNNMax + "");


        //clear all packet buffers at end
        baselinePackets.clear();
        testPackets.clear();
        maxPackets.clear();
    }

    public boolean hasAtLeastOnePacket(String data) {

        int hexIdx = data.indexOf("#");
        //search for the pipe index (end of packet) AFTER the hex index.
        int pipeIdx = data.indexOf("|", hexIdx);

        //if we can find a hex and a following pipe index
        if (hexIdx != -1 && pipeIdx != -1) {
            return true;
        } else {
            return false;
        }


    }

    public boolean packetEndsProperly(String packet) {
        return packet.charAt(packet.length() - 1) == '|';
    }

    public boolean isDoubleZero(double doubleToCheck) {
        //NOTE - VERY BAD PRACTISE BY ME, SORRY - USES A GLOBAL VARIABLE AS CONFIDENCE THRESHOLD FOR DOUBLE ZERO EQUALITY

        return doubleToCheck >= -doubleZeroThreshold && doubleToCheck <= doubleZeroThreshold;
    }

    //gets the length of a list of packets (duration elapsed between first and last)
    public double getPacketListDuration(ArrayList<Packet> packets) {
        double startTime = packets.get(0).getTimeElapsed();
        double endTime = packets.get(packets.size() - 1).getTimeElapsed();

        return endTime - startTime;
    }

    //gets the last x miliseconds of data
    public ArrayList<Packet> getLastXMsecData(ArrayList<Packet> packets, double msec) {
        ArrayList<Packet> packetsToReturn = new ArrayList<>();

        //grab the last packet and check the timing, minus msec from it to get the cutoff
        double msecCutoff = packetsToReturn.get(packetsToReturn.size() - 1).getTimeElapsed() - msec;

        for (int i = packets.size() - 1; i >= 0; i--) {
            if (packets.get(i).getTimeElapsed() >= msecCutoff) {
                packetsToReturn.add(packets.get(i));
            } else {
                //we have reached the cutoff point, break
                break;
            }
        }

        return packetsToReturn;
    }

    //gets the first x miliseconds of data
    //also, RETURN NOTHING IF WE HAVE EXCEEDED THE SIZE OF THE ARRAY
    //we want to make sure that we are returning a SUBSET
    public ArrayList<Packet> getXMsecDataFromIndex(ArrayList<Packet> packets, double msec, int startIdx) {

        Log.d("getxmsec", "packets len: " + packets.size() + " msec: "  + msec + " startidx: " + startIdx);

        ArrayList<Packet> packetsToReturn = new ArrayList<>();

        //grab the last packet and check the timing, add msec from it to get the cutoff
        double msecCutoff = packets.get(startIdx).getTimeElapsed() + msec;

        Log.d("mseccutoff", "Start time: " + (msecCutoff - msec) + " | Msec cutoff: " + msecCutoff);
        for (int i = startIdx; i < packets.size(); i++) {
            Log.d("time elapsed for packet", "Packet idx: " + i + " | time : " + packets.get(i).getTimeElapsed());
            if (packets.get(i).getTimeElapsed() <= msecCutoff) {
                packetsToReturn.add(packets.get(i));
            } else {
                //we have reached the cutoff point, return values
                Log.d("packetstoreturn", "len of packets to return: " + packetsToReturn.size());
                return packetsToReturn;
            }
        }

        //we reached the end of the array, that means we are no longer taking a subset. return an empty Arraylist
        Log.d("end of array", "reached end of array for : "  +  "packets len: " + packets.size() + " msec: "  + msec + " startidx: " + startIdx);
        return new ArrayList<Packet>();

    }


    //calculate the percentage of samples in the packet list that have useful GSR data
    public double getPercentUsefulGSR(ArrayList<Packet> packets) {
        int usefulSamples = 0;
        int totalSamples = packets.size();
        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                usefulSamples++;
            }
        }

        return (usefulSamples / totalSamples) * 100;
    }

    //calculate the percentage of samples in the packet list that have useful IBI data
    public double getPercentUsefulIBI(ArrayList<Packet> packets) {
        int usefulSamples = 0;
        int totalSamples = packets.size();
        for (Packet packet : packets) {
            if (packet.getIBI() > ibiCutoff) {
                usefulSamples++;
            }
        }

        return (usefulSamples / totalSamples) * 100;
    }

    //calculate a GSR Stress value from the average GSR
    public double getGSRStressPercentage(double avgGsr) {
        if (avgGsr < gsrMin) {
            //not stressed and below the normal min
            gsrMin = avgGsr;
            return 0;
        } else if (gsrMin <= avgGsr && avgGsr < gsr33) {
            //not stressed
            return ((avgGsr - gsrMin)
                          /
                    (gsr33 - gsrMin))   * (33);
        } else if (gsr33 <= avgGsr && avgGsr < gsr66) {
            //stressed
            return 33 + (((avgGsr - gsr33) /
                         (gsr66 - gsr33)) * (33));
        } else if (gsr66 <= avgGsr && avgGsr < gsrMax) {
            return 66 + (((avgGsr - gsr66) /
                         (gsrMax - gsr66)) * (34));
        } else if (avgGsr >= gsrMax) {
            gsrMax = avgGsr;
            return 100;
        } else {
            Log.e("out of bounds", "getGSR Stress Percentage failure!");
            return 0; //SHOULD NOT HAPPEN
        }
    }

    //calculate a SDNN Stress value from the average SDNN
    public double getSDNNStressPercentage(double SDNN) {
        if (SDNN > SDNNMin) {
            //not stressed and below the normal min
            SDNNMin = SDNN;
            return 0;
        } else if (SDNN <= SDNNMin  && SDNN >= SDNN33) {
            //not stressed
            return ((SDNNMin - SDNN) / (SDNNMin - SDNN33)) * (33);
        } else if (SDNN <= SDNN33 && SDNN >= SDNN66) {
            //stressed
            return 33 + (((SDNN33 - SDNN) / (SDNN33 - SDNN66)) * (33));
        } else if (SDNN66 <= SDNN && SDNN < SDNNMax) {
            return 66 + (((SDNN66 - SDNN) / (SDNN66 - SDNNMax)) * (33));
        } else if (SDNN < SDNNMax) {
            SDNNMax = SDNN;
            return 100;
        } else {
            Log.e("out of bounds", "getSDNN Stress Percentage failure!");
            return 0; //SHOULD NOT HAPPEN
        }
    }

    //select the right packets and process them
    public double getGSRStress(ArrayList<Packet> packets) {
        double gsrAccumulator = 0;
        double gsrCount = 0;

        for (Packet packet : packets) {
            if (packet.getGSR() > gsrCutoff) {
                gsrAccumulator += packet.getGSR();
                gsrCount++;
            }
        }

        double averageGSR = gsrAccumulator / gsrCount;
        return getGSRStressPercentage(averageGSR);


    }

    //select the right packets and process them
    public double getIBIStress(ArrayList<Packet> packets) {


        double ibiAccumulator = 0;
        double ibiCount = 0;

        //getting the mean and number of useful packets
        for (Packet packet : packets) {
            if (packet.getIBI() > ibiCutoff) {
                ibiAccumulator += packet.getIBI();
                ibiCount++;
            }
        }

        //average calculated
        double averageIBI = ibiAccumulator / ibiCount;

        //CALCULATE SDNN:
        // = SQRT( (1 / ibiCount) * sumofall((ibiVal - ibiMean)^2))

        //all (xi - xavg) ^ 2 - calculate squared sum
        double sumSquared = 0;
        for (Packet packet : packets) {
            if (packet.getIBI() > ibiCutoff) {
                sumSquared += Math.pow((packet.getIBI() - averageIBI), 2);
            }
        }

        //final SDNN value
        double SDNN = Math.sqrt((1 / ibiCount) * sumSquared);
        Log.d("SDNN", "Sdnn: " + SDNN);

        //calculate the stress percentage based SDNN
        //do 100 MINUS because SDNN is inverse to the actual stress
        return getSDNNStressPercentage(SDNN);


    }

    public double getStressIndex(ArrayList<Packet> packets) {
        //we assume that we have received EXACTLY 60 seconds of data

        //firstly, get the reliability of the data
        double percentUsefulGSR = getPercentUsefulGSR(packets);
        double percentUsefulIBI = getPercentUsefulIBI(packets);

        //check the percentage of good data, get a boolean indicating for both cases
        boolean gsrIsUseful = percentUsefulGSR <= percentageCutoff;
        boolean ibiIsUseful = percentUsefulIBI <= percentageCutoff;

        //default values to check for
        double gsrStress = 0;
        double ibiStress = 0;


        if (gsrIsUseful) {
            gsrStress = getGSRStress(packets);
        }

        if (ibiIsUseful) {
            ibiStress = getIBIStress(packets);
        }

        double stressSum = gsrStress + ibiStress;

        //return a stress value depending on what data is useful
        if (gsrIsUseful && ibiIsUseful) {
            return (gsrStress + ibiStress) / 2;
        } else if ((gsrIsUseful && !ibiIsUseful) || (!gsrIsUseful && ibiIsUseful)) {
            return stressSum;
        } else {
            Log.e("bad data", "Both gsr and ibi - useless data!");
            return -10;
        }

    }

    public void dataExtractor(String sentValues) {

        Log.d("data", "Sentvalues: " + sentValues);

        //STRING SPLIT TO GET PACKETS

        //PRE-PROCESSING/
        String inputData = recDataString.toString();

        if (firstData) {
            //properly set the next compute time (TO NOW + 1min)
            nextComputeTime = Calendar.getInstance().getTimeInMillis() + msecBetweenComputes;
            //trim the incoming data to make sure we start with a #
            inputData = trimStart(inputData);
            firstData = true;
            Log.d("data", "Post-trim: " + inputData);
        }

        //get the actual packet data
        String stringed = recDataString.toString();
        //split by | to get full packets
        String[] splitPackets = stringed.split(Pattern.quote("|"));

        //packets to process depends on whether the last packet is a full packet (ie ends with '|')
        String[] packetsToProcess;

        //check if the last packet ends with a |, split the packets to process accordingly
        if (packetEndsProperly(stringed)) {
            packetsToProcess = splitPackets;

            //reset the string accumulator
            recDataString.setLength(0);
        } else {
            //don't process the last packet, add back to accumulator
            packetsToProcess = Arrays.copyOfRange(splitPackets, 0, splitPackets.length - 2);

            String lastPacket = splitPackets[splitPackets.length - 1];
            recDataString.setLength(0);
            recDataString.append(lastPacket);
        }

        //ACTUAL PACKET PROCESSING CODE
        for (String packet : packetsToProcess) {
            int gsr_idx = packet.indexOf("+") + 1;
            int time_elapsed_idx = packet.indexOf("$") + 1;

            double ibi = Double.parseDouble(packet.substring(1, gsr_idx - 1));
            double gsr = Double.parseDouble(packet.substring(gsr_idx, time_elapsed_idx - 1));
            double time_elapsed = Double.parseDouble(packet.substring(time_elapsed_idx));

            //update graph
            gsrSeries.appendData(new DataPoint(time_elapsed, gsr), true, numPointsToDisplay);
            ibiSeries.appendData(new DataPoint(time_elapsed, ibi), true, numPointsToDisplay);

            //Log.d("data", "ibi: " + ibi + " ||| gsr: " + gsr + " ||| time_elapsed: " + time_elapsed);
            outText.setText("ibi: " + ibi + " ||| gsr: " + gsr + " ||| time_elapsed: " + time_elapsed);

            Packet newPacket = new Packet(gsr, time_elapsed, ibi);

            //add the packet to the correct arraylist
            if (calibrationHappening) {
                if (currentCalibrationState == CALIBRATION_STATE.DURING_BASELINE) {
                    baselinePackets.add(newPacket);
                    Log.d("data", "baseline length " + baselinePackets.size());
                } else if (currentCalibrationState == CALIBRATION_STATE.DURING_TEST) {
                    testPackets.add(newPacket);
                    Log.d("data", "test length " + testPackets.size());
                } else if (currentCalibrationState == CALIBRATION_STATE.DURING_MAX) {
                    maxPackets.add(newPacket);
                    Log.d("data", "max length " + maxPackets.size());
                }
            } else {
                dataPackets.add(newPacket);
                Log.d("data", "datapackets length " + dataPackets.size());
            }


        }


        //CHECK IF TIME TO COMPUTE HAS ARRIVED
        if (Calendar.getInstance().getTimeInMillis() > nextComputeTime && !calibrationHappening) {
            //do computation
            double stressIndex = getStressIndex(dataPackets);
            Log.d("STRESSINDEX", stressIndex +"");
            stressSeries.appendData(new DataPoint(Calendar.getInstance().getTimeInMillis(), stressIndex), true, numPointsToDisplay);
            //Toast.makeText(getApplicationContext(), stressIndex + "", Toast.LENGTH_LONG).show();
            //clear packets
            dataPackets.clear();

            //set next compute time
            nextComputeTime = Calendar.getInstance().getTimeInMillis() + msecBetweenComputes;
        } else {
            Log.d("time", "time left: " + (nextComputeTime - Calendar.getInstance().getTimeInMillis()));
        }



    }

    public static double averager(ArrayList<Double> values) {
        double sum = 0;

        for (Double num : values) {
            sum += num;
        }

        return (sum / values.size());
    }


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return device.createRfcommSocketToServiceRecord(MY_UUID);
        //creates secure outgoing connection with BT device using UUID
    }

    @Override
    public void onResume() {


        super.onResume();
        //create device and set the MAC address
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
        }
        // Establish the Bluetooth socket connection.
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                //insert code to deal with this
            }
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not, an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("x");

    }


    @Override
    public void onPause() {
        super.onPause();
        try {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }


    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if (btAdapter == null) {
            //Device does not support bluetooth"
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create B to Bluetooth I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {

                    // read from the Input Stream.
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    mHandler.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();

            }
        }
    }

    class Packet {

        private double gsr;
        private double time_elapsed;
        private double ibi;

        public Packet(double gsr, double time_elapsed, double ibi) {

            this.gsr = gsr;
            this.time_elapsed = time_elapsed;
            this.ibi = ibi;
        }

        public double getGSR() {
            return this.gsr;
        }
        public double getIBI() {
            return this.ibi;
        }

        public double getTimeElapsed() {
            return this.time_elapsed;
        }
    }
}







