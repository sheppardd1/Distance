package com.example.davea.distance;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

//import com.example.davea.distance.R;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //UI:
    public Button BtnStart, BtnClear;
    public TextView TV1;
    public TextView TV2;

    //variables:
    public float xAv;
    public float yAv;
    public float zAv;
    public float x, y, z;
    public long lastUpdateTime = 0;
    public int i = 0;
    public boolean on = true;
    public double distanceAx = 0;
    public double distanceAy = 0;
    public double distanceAz = 0;
    public double distanceBx = 0;
    public double distanceBy = 0;
    public double distanceBz = 0;
    public double distanceABx = 0;
    public double distanceABy = 0;
    public double distanceABz = 0;
    public float speed0x = 0;
    public float speed0y = 0;
    public float speed0z = 0;
    public double distanceTraveledAB = 0;
    public double distanceTraveledA = 0;
    public double distanceTraveledB = 0;
    public double netAcceleration = 0;


    //sensors:
//    public Sensor accelerometer;
//    public SensorManager sensorManager;
//    public SensorManager RVSensorManager;
//    public Sensor RVSensor;

    public SensorManager LASensorManager;
    public Sensor LASensor;

    //constants:
    final public int INTERVAL = 200;
    final public int NUMBER_OF_POINTS_TO_AVERAGE = 5;
    //how often we calculate the average acceleration (s):
    final public float TIME_INTERVAL = ((float)INTERVAL / 1000) * (float) NUMBER_OF_POINTS_TO_AVERAGE;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setup();    //assign and setup everything


        BtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on = !on;
                i = 0;
            }
        });

        BtnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on = false;
                i = 0;
                TV1.setText("");
                TV2.setText("");
                distanceTraveledAB = 0;
                distanceTraveledA = 0;
                distanceTraveledB = 0;
            }
        });
    }

    public void setup() {
        //set up accelerometer:
        /*sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert sensorManager != null;   //ensures next line does not return null pointer exception
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, sensorManager.SENSOR_DELAY_NORMAL);*/

        //set up rotation vector sensor:
    /*  RVSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert RVSensorManager != null;
        RVSensor = RVSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        assert RVSensor != null;
        RVSensorManager.registerListener(this, RVSensor, RVSensorManager.SENSOR_DELAY_NORMAL);*/

        //set up Linear acceleration sensor:
        LASensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert LASensorManager != null;
        LASensor = LASensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        assert LASensor != null;
        LASensorManager.registerListener(this, LASensor, LASensorManager.SENSOR_DELAY_NORMAL);

        //buttons:
        BtnStart = findViewById(R.id.Start);
        BtnClear = findViewById(R.id.Clear);
        //textViews:
        TV1 = findViewById(R.id.TV1);
        TV2 = findViewById(R.id.TV2);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        //if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        if (System.currentTimeMillis() - lastUpdateTime > INTERVAL && on) {
            lastUpdateTime = System.currentTimeMillis();

            x = event.values[0];
            y = event.values[1];
            z = event.values[2];


            //compute collective/cumulative average instead of accumulating values and later getting average
            xAv = (xAv * i + x) / (i + 1);
            yAv = (yAv * i + y) / (i + 1);
            zAv = (zAv * i + z) / (i + 1);

            if (i == 0){
                distanceAx = 0;
                distanceBx = 0;
                distanceABx = 0;
                distanceAy = 0;
                distanceBy = 0;
                distanceABy = 0;
                distanceAz = 0;
                distanceBz = 0;
                distanceABz = 0;
            } else if (i >= NUMBER_OF_POINTS_TO_AVERAGE - 1) {
                TV1.setText("X: " + xAv + "\n" + "Y: " + yAv + "\n" + "Z: " + zAv);
                i = 0;
                xAv = yAv = zAv = 0;
            } else i++;



            //Now, get distance:
            //Method 1: dx = v0 * t + 0.5 * a * t^2
            distanceAx = speed0x * TIME_INTERVAL + 0.5 * xAv * TIME_INTERVAL * TIME_INTERVAL;
            distanceAy = speed0y * TIME_INTERVAL + 0.5 * yAv * TIME_INTERVAL * TIME_INTERVAL;
            distanceAz = speed0z * TIME_INTERVAL + 0.5 * zAv * TIME_INTERVAL * TIME_INTERVAL;
            float oldSpeed0x = speed0x;
            float oldSpeed0y = speed0y;
            float oldSpeed0z = speed0z;
            speed0x += xAv * TIME_INTERVAL;  //from: a * t = v - v0
            speed0y += yAv * TIME_INTERVAL;
            speed0z += zAv * TIME_INTERVAL;

            //Method2: using dx = (v^2 - v0^2) / (2 * a)
            distanceBx = ((speed0x * speed0x) - (oldSpeed0x * oldSpeed0x)) / (2 * xAv);
            distanceBy = ((speed0y * speed0y) - (oldSpeed0y * oldSpeed0y)) / (2 * yAv);
            distanceBz = ((speed0z * speed0z) - (oldSpeed0z * oldSpeed0z)) / (2 * zAv);

            distanceABx = (distanceAx + distanceBx) / 2;
            distanceABy = (distanceAy + distanceBy) / 2;
            distanceABz = (distanceAz + distanceBz) / 2;

            distanceTraveledA += triplePythagorean(distanceAx, distanceAy, distanceAz);
            distanceTraveledB += triplePythagorean(distanceBx, distanceBy, distanceBz);
            distanceTraveledAB = ((distanceTraveledA + distanceTraveledB) / 2);

            TV2.setText("A: " + distanceTraveledA + "\nB: " + distanceTraveledB + "\nAverage: " + distanceTraveledAB);
        }

        /*}else if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            TVData.setText("\nX: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: " + event.values[2]);
        }*/
    }



/*    @Override
    public void onSensorChanged(SensorEvent event) {

        if (System.currentTimeMillis() - lastUpdateTime > INTERVAL && on) {
            lastUpdateTime = System.currentTimeMillis();

            x = event.values[0];
            y = event.values[1];
            z = event.values[2];


            //compute collective/cumulative average instead of accumulating values and later getting average
            xAv = (xAv * i + x) / (i + 1);
            yAv = (yAv * i + y) / (i + 1);
            zAv = (zAv * i + z) / (i + 1);

            if (i >= NUMBER_OF_POINTS_TO_AVERAGE - 1) {
                TVAverage.setText("X: " + xAv + "\n" + "Y: " + yAv + "\n" + "Z: " + zAv);
                i = 0;
                xAv = yAv = zAv = 0;
            } else i++;

            TVData.setText("X: " + x + "\n" + "Y: " + y + "\n" + "Z: " + z + "\n" + "i: " + i);

        }

    }*/

    private double triplePythagorean(double a, double b, double c) {
        return (Math.sqrt((a * a) + (b * b)) + (c * c));
    }//not used yet


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used, but must be included for this to work
    }

}

