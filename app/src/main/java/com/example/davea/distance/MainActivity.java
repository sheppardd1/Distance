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
import android.widget.Toast;

import java.sql.SQLSyntaxErrorException;

//import com.example.davea.distance.R;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //UI:
    public Button BtnStart, BtnClear;
    public TextView TV1;
    public TextView TV2;

    //variables:
    public float averageAcceleration[] = new float[3];
    public float x, y, z;
    public double lastUpdateTime = 0;
    public int i = 0;
    public boolean on = true;
    public double distanceA[] = new double[3];
    //public double distanceB[] = new double[3];
    //public double distanceAB[] = new double[3];
    public double speed0[] = new double[3];
    double oldSpeed0[] = new double[3];
    //public double distanceTraveledAB[] = new double[3];
    public double distanceTraveledA[] = new double[3];
    //public double distanceTraveledB[] = new double[3];
    public double totalDistance = 0;
    public double netAcceleration = 0;
    public double startTime = 0;
    public double elapsedTime = 0;
    public double totalTime = 0;

    //sensors:
    public Sensor accelerometer;
    public SensorManager sensorManager;
    public SensorManager LASensorManager;
    public Sensor LASensor;

    //constants:
    final public int INTERVAL = 200;
    final public int NUMBER_OF_POINTS_TO_AVERAGE = 2;
    final public float EPSILON = (float) 1.8;



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
                x = y = z = 0;
                TV1.setText("");
                TV2.setText("");
                reset();
            }
        });
    }

    public void setup() {

        //Try to set up using Linear Acceleration sensor, which neglects gravity
        //If the device does not have the LA sensor, then use regular accelerometer for testing purposes
        //set up Linear acceleration sensor:
        LASensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert LASensorManager != null;
        LASensor = LASensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(LASensor != null) {
            LASensorManager.registerListener(this, LASensor, LASensorManager.SENSOR_DELAY_NORMAL);
        }
        else {  //for testing purposes only:
            //if no LA sensor on device, set up accelerometer as alternative:
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            assert sensorManager != null;   //ensures next line does not return null pointer exception
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, sensorManager.SENSOR_DELAY_NORMAL);
            Toast.makeText(this, "REVERTING TO ACCELEROMETER\nACCURACY REDUCED\nUSE FOR TESTING PURPOSES ONLY", Toast.LENGTH_LONG).show();
        }

        //set up rotation vector sensor:
    /*  RVSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert RVSensorManager != null;
        RVSensor = RVSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        assert RVSensor != null;
        RVSensorManager.registerListener(this, RVSensor, RVSensorManager.SENSOR_DELAY_NORMAL);*/

        //buttons:
        BtnStart = findViewById(R.id.Start);
        BtnClear = findViewById(R.id.Clear);
        //textViews:
        TV1 = findViewById(R.id.TV1);
        TV2 = findViewById(R.id.TV2);

        reset();
    }

    public void reset(){
        totalDistance = 0;
        i = 0;
        for(int j = 0; j < 3; j++){
            distanceA[j] = 0;
            //distanceAB[j] = 0;
            //distanceB[j] = 0;
            distanceTraveledA[j] = 0;
            //distanceTraveledB[j] = 0;
            //distanceTraveledAB[j] = 0;
            speed0[j] = 0;
            oldSpeed0[j] = 0;
            averageAcceleration[j] = 0;
            startTime = 0;
            totalTime = 0;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (System.currentTimeMillis() - lastUpdateTime > INTERVAL && on) {
            lastUpdateTime = System.currentTimeMillis();

            x = event.values[0];
            y = event.values[1];
            z = event.values[2];
            //TV1.setText("Acceleration:\nX: " + x + "\nY: " + y + "\nZ: " + z + "\nCurrent Distance:\nx: " + distanceA[0] + "\ny: " + distanceA[1] + "\nz: "
            //       + distanceA[2]);

            //compute collective/cumulative average instead of accumulating values and later getting average

            averageAcceleration[0] = (averageAcceleration[0] * i + x) / (i + 1);
            averageAcceleration[1] = (averageAcceleration[1] * i + y) / (i + 1);
            averageAcceleration[2] = (averageAcceleration[2] * i + z) / (i + 1);

            if (i == 0){
                for(int j = 0; j < 3; j++) {
                    startTime = System.currentTimeMillis();
                    distanceA[j] = 0;
                }
            }

            if (i >= NUMBER_OF_POINTS_TO_AVERAGE - 1) {

                //elapsedTime should be about the same as TIME_INTERVAL, but using elapsedTime ensures that the true time is used
                elapsedTime = (System.currentTimeMillis() - startTime) / (long)1000.0;
                totalTime += elapsedTime;

                //oldSpeed0 = speed0:
                System.arraycopy(speed0, 0, oldSpeed0, 0, 3);

                //set speed (future v0) every TIME_INTERVAL seconds
                for(int j = 0; j < 3; j++) {
                    speed0[j] += averageAcceleration[j] * elapsedTime; //from: a * t = v - v0
                    if(speed0[j] >= 0){
                        speed0[j] -= (EPSILON * elapsedTime*elapsedTime * 0.5);
                    }else {
                        speed0[j] += (EPSILON * elapsedTime * elapsedTime * 0.5);
                    }
                }

                //Now, get distance:
                //dx = v0 * t + 0.5 * a * t^2
                for(int j = 0; j < 3; j++) {
                    distanceA[j] = (oldSpeed0[j] * elapsedTime + 0.5 * averageAcceleration[j] * elapsedTime * elapsedTime);
                }

                for(int j = 0; j < 3; j++) {
                    distanceTraveledA[j] += distanceA[j];
                    //account for error: distance_calibrated = distance_calculated - EPSILON * t^2 * 0.5
                    /*if(distanceTraveledA[j] >= 0){
                        distanceTraveledA[j] -= (EPSILON * elapsedTime*elapsedTime * 0.5);
                    }else {
                        distanceTraveledA[j] += (EPSILON * elapsedTime * elapsedTime * 0.5);
                    }*/
                }

                //totalDistance = triplePythagorean(distanceTraveledA[0], distanceTraveledA[1], distanceTraveledA[2]);

                TV2.setText("Cumulative:\nX: " + distanceTraveledA[0] + "\nY: " + distanceTraveledA[1] + "\nZ: "
                        + distanceTraveledA[2] + "\nTotal: " + totalDistance + "\nElapsed Time: " + elapsedTime
                        + "\nTotal Time: " + totalTime);

                TV1.setText("Acceleration:\nX: " + averageAcceleration[0] + "\nY: " + averageAcceleration[1] + "\nZ: "
                        + averageAcceleration[2] + "\nCurrent Distance:\nx: " + distanceA[0] + "\ny: " + distanceA[1] + "\nz: "
                        + distanceA[2] + "\nSpeed:\nX: " + speed0[0] + "\nY: " + speed0[1] + "\nZ: " + speed0[2]);
                //TV1.setText("Accelerations\nX: " + averageAcceleration[0] + "\n" + "Y: " + averageAcceleration[1] + "\n" + "Z: " + averageAcceleration[2]);
                i = 0;

            } else i++;
        }
    }

    private double triplePythagorean(double a, double b, double c) {
        return (Math.sqrt((a * a) + (b * b) + (c * c)));
    }//not used yet

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used, but must be included for this to work
    }
}

/* Stopped using method 2 since results were the same as method 1 to aroud 15 decimal places

//Method2: using dx = (v^2 - v0^2) / (2 * a)
                for(int j = 0; j < 3; j++) {
        if(averageAcceleration[j] != 0) {
        distanceB[j] = ((speed0[j] * speed0[j]) - (oldSpeed0[j] * oldSpeed0[j])) / (2 * averageAcceleration[j]);
        }else distanceB[j] = oldSpeed0[j] * elapsedTime;
        }

        for(int j = 0; j < 3; j++) {
        distanceAB[j] = (distanceA[j] + distanceB[j]) / 2;
        }


        for (int j = 0; j < 3; j++) {
        distanceTraveledA[j] += distanceA[j];
        distanceTraveledB[j] += distanceB[j];
        distanceTraveledAB[j] = ((distanceTraveledA[j] + distanceTraveledB[j]) / 2);
        }

        totalDistance = triplePythagorean(distanceTraveledAB[0], distanceTraveledAB[1], distanceTraveledAB[2]);
        */