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

    /*
        END OF SETTINGS
     */

    //private final Handler mHandler = new Handler();

    //whatever is needed for data extraction
    static ArrayList<Packet> dataPackets = new ArrayList<Packet>();


    //to plot realtime graph
    private Runnable mTimer1;  //GSR = 1

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

    public void updateCalibrationValues() {
        //have 3 arraylists, baselinePackets, testPackets and maxPackets

        // ---- GSR SECTION ----
        double baselineAvg = getAverageGSR(baselinePackets);
        double testAvg = getAverageGSR(testPackets);
        double maxAvg = getAverageGSR(maxPackets);

        double baselineSD = getSDGSR(baselinePackets);
        double testSD = getSDGSR(testPackets);
        double maxSD = getSDGSR(maxPackets);

        

        // ---- END GSR SECTION ----

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

    /*
    //gets the last x miliseconds of data
    public ArrayList<Packet> getLastXMsecData(ArrayList<Packet> packets) {
        ArrayList<Packet> packetsToReturn
    }
    */

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
                    (gsr33 - gsrMin))   * (33 / 100);
        } else if (gsr33 <= avgGsr && avgGsr < gsr66) {
            //stressed
            return 33 + (((avgGsr - gsr33) /
                         (gsr66 - gsr33)) * (33 / 100));
        } else if (gsr66 <= avgGsr && avgGsr < gsrMax) {
            return 66 + (((avgGsr - gsr66) /
                         (gsrMax - gsr66)) * (34 / 100));
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
        if (SDNN < SDNNMin) {
            //not stressed and below the normal min
            SDNNMin = SDNN;
            return 0;
        } else if (SDNNMin <= SDNN && SDNN < SDNN33) {
            //not stressed
            return ((SDNN - SDNNMin)
                    /
                    (SDNN33 - SDNNMin))   * (33 / 100);
        } else if (SDNN33 <= SDNN && SDNN < SDNN66) {
            //stressed
            return 33 + (((SDNN - SDNN33) /
                    (SDNN66 - SDNN33)) * (33 / 100));
        } else if (SDNN66 <= SDNN && SDNN < SDNNMax) {
            return 66 + (((SDNN - SDNN66) /
                    (SDNNMax - SDNN66)) * (34 / 100));
        } else if (SDNN >= SDNNMax) {
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







