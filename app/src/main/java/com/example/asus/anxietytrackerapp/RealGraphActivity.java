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
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class RealGraphActivity extends Activity {

    //private final Handler mHandler = new Handler();

    //whatever is needed for data extraction
    static ArrayList<Packet> dataPackets = new ArrayList<Packet>();
    //initial time to compute
    static int next_computation_time = 60000;
    //how much time to add after each compute
    static int computation_step = 60000;
    //the current index to compute from
    static int compute_index_from = 0;

    //to plot realtime graph
    private Runnable mTimer1;  //GSR = 1
    private GraphView gsrgraph;
    private LineGraphSeries<DataPoint> gsrSeries;
    private ArrayList<DataPoint> seriesGSR;


    //DATA READINGS VARS
    boolean firstData = false;


    int dataCount = 1;
    int timeXAxis = 1;

    //User Interface Variables
    //COMMENTED THIS OUT - DEFINED ONCE ABOVE
    private Handler mHandler;
    final int handlerState = 0;

    //Data Output
    private BluetoothAdapter btAdapter = null;
    //to create connection to the hc-05 module
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;


    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //specify the MAC address of the Bluetooth address that you want to connect to
    private static String address = "98:D3:31:70:4D:02";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_readings);
        //setContentView(R.layout.activity_feedback_control);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Toast.makeText(this, "onCreate, ReadGraph..", Toast.LENGTH_LONG).show();
        seriesGSR = new ArrayList<DataPoint>();


//Thread handlers are implemented in the main thread of an application and are primarily used to make updates to the user interface in response
// to messages sent by another thread running within the application’s process.
        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == handlerState) {       //if message is what we want
                    Toast.makeText(getApplicationContext(), "hi", Toast.LENGTH_LONG).show();

                    // Gets the string message from the incoming Message object.
                    String readMessage = (String) msg.obj;

                    //append the message to the recDataString - overall data
                    recDataString.append(readMessage);

                    //check if we have >= 1 packet inside
                    if (hasAtLeastOnePacket(recDataString.toString())) {
                        ((TextView)findViewById(R.id.dataText)).setText(recDataString);
                        dataExtracter(recDataString.toString());
                    }




                    //StringBuilder sensordata = recDataString;             //get sensor value from string
                    //sensorView.setText(P.getGSR);    //update the textviews with sensor values
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();

        // init series data for gsr
        //gsrSeries = new LineGraphSeries(new GraphViewData[]{});
/*
        gsrSeries = new LineGraphSeries<DataPoint>(seriesGSR.toArray());
        //gsrgraph = new LineGraphView(this, "GSRGraph");  //context, heading
        gsrgraph = (GraphView) findViewById(R.id.graph);
        gsrgraph.addSeries(gsrSeries); // data
*/
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

        //remove until first #

    }

    public boolean isFullPacket(String data) {
        //return data.indexOf("#" )


    }

    public boolean hasAtLeastOnePacket(String data) {
        int hexIdx = data.indexOf("#");
        int pipeIdx = data.indexOf("|");

        


    }

    public void dataExtracter(String sentValues) {

        Log.d("data", "Sentvalues: " + sentValues);

        //STRING SPLIT TO GET PACKETS

        //PRE-PROCESSING/
        String inputData = recDataString.toString();
        //trim the incoming data to make sure we start with a #
        if (firstData) {
            inputData = trimStart(inputData);
            firstData = true;
        }




        String stringed = recDataString.toString();
        String[] splitPackets = stringed.split(Pattern.quote("|"));

        for (int i = 0; i < splitPackets.length; i++) {
            Log.d("data", splitPackets[i]);
        }

        recDataString.setLength(0);



/*
        int gsr_idx = sentValues.indexOf("+") + 1;
        int time_elapsed_idx = sentValues.indexOf("$") + 1;


        //Extracting the values
        double gsr = Double.parseDouble(sentValues.substring(gsr_idx, time_elapsed_idx - 1));
        Log.d("data", "gsr: " + gsr);
        int time_elapsed = Integer.parseInt(sentValues.substring(time_elapsed_idx));
        Log.d("data", "time elapsed: " + sentValues);

        dataPackets.add(new Packet(gsr, time_elapsed));

        //time_elapsed_idx is the latest packet time
        if (time_elapsed > next_computation_time) {
            //we have reached the next compute step
            //get SDNN
            //set initial SDNN counter to 1
            //can use subList(inclusive, exclusive) to get a sublist of the arraylist
            List<Packet> packetsToProcess = dataPackets.subList(compute_index_from, dataPackets.size() - 1);


            ArrayList<Double> allGSRValues = new ArrayList<Double>();

            for (Packet p : packetsToProcess) {
                allGSRValues.add(p.getGSR());
            }

            // find average value
            //double avgIBI = averager(allIBIValues);
            double avgGSR = averager(allGSRValues);
            //seriesGSR.add(new DataPoint(timeXAxis, avgGSR));

            Log.d("data", "timex | avgsr: " + timeXAxis + "|" + avgGSR);

            //seriesGSR.add(new GraphViewData(timeXAxis, avgGSR));
            timeXAxis++;

            //System.out.println("Std Dev: " + stdDev(allIBIValues, avgIBI));


            //finding std dev for ibi

            // printList(allGSRValues);

            // for(Packet p : packetsToProcess){
            // 	System.out.println(p.getRepr());
            // }

            //to get the relevant portion of packets from the datapackets - use
            //dataPackets.subList(compute_index_from, datapackets.size() - 1);

            //add the required compute time
            next_computation_time += computation_step;
            //set the next compute index to the correct point
            compute_index_from = dataPackets.size();
        }
*/
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
        private int time_elapsed;

        public Packet(double gsr, int time_elapsed) {

            this.gsr = gsr;
            this.time_elapsed = time_elapsed;
        }

        public double getGSR() {
            return this.gsr;
        }
    }
}







