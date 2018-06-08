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
    public double lastUpdateTime = 0;
    public int i = 0;
    public boolean on = true;
    public float shortDistance[] = new float[3];
    public float speed0[] = new float[3];
    public float oldSpeed0[] = new float[3];
    public float totalDistance[] = new float[3];
    public float combinedTotalDistance = 0;
    public double startTime = 0;
    public double computationTime = 0;
    public double totalTime = 0;
    public float totalDistanceCalibrated[] = new float[3];

    //sensors:
    public Sensor accelerometer;
    public SensorManager sensorManager;
    public SensorManager LASensorManager;
    public Sensor LASensor;

    //constants:
    final public int INTERVAL = 250;
    final public int NUMBER_OF_POINTS_TO_AVERAGE =2;
    final public float EPSILON = (float) 0.04;
    public float Y_AXIS_CORRECTION = (float) -0.24;


//NOTE: ONLY THE Y-AXIS IS CURRENTLY BEING TESTED - THE X AND Z AXES ARE UNCALIBRATED


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
                if(!on) {
                    TV1.setText("");
                    TV2.setText("");
                    reset();
                }
                else on = false;
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


        //buttons:
        BtnStart = findViewById(R.id.Start);
        BtnClear = findViewById(R.id.Clear);
        //textViews:
        TV1 = findViewById(R.id.TV1);
        TV2 = findViewById(R.id.TV2);

        reset();
    }

    public void reset(){
        combinedTotalDistance = 0;
        i = 0;
        for(int j = 0; j < 3; j++){
            shortDistance[j] = 0;
            totalDistance[j] = 0;
            totalDistanceCalibrated[j] = 0;
            speed0[j] = 0;
            oldSpeed0[j] = 0;
            averageAcceleration[j] = 0;
            startTime = 0;
            totalTime = 0;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on) {

            //compute collective/cumulative average instead of accumulating values and later getting average
            for(int j = 0; j < 3; j++) {
                averageAcceleration[j] = (averageAcceleration[j] * i + event.values[j] - Y_AXIS_CORRECTION) / (i + 1);
            }

            if (i == 0){
                startTime = System.currentTimeMillis();
            }

            if (i >= NUMBER_OF_POINTS_TO_AVERAGE - 1) {

                //computationTime should be about the same as TIME_INTERVAL, but using computationTime ensures that the true time is used, esp. for slower machines
                computationTime = (System.currentTimeMillis() - startTime) / (long)1000;
                totalTime += computationTime;

                //oldSpeed0 = speed0:
                System.arraycopy(speed0, 0, oldSpeed0, 0, 3);

                //set speed (future v0) every computationTime seconds ~ TIME_INTERVAL
                for(int j = 0; j < 3; j++) {
                    speed0[j] += averageAcceleration[j] * computationTime; //from: a * t = v - v0
                    /*if(speed0[j] >= 0){
                        speed0[j] -= (EPSILON * computationTime*computationTime * 0.5);
                    }else {
                        speed0[j] += (EPSILON * computationTime * computationTime * 0.5);
                    }*/
                }

                //Now, get distance:
                //dx = v0 * t + 0.5 * a * t^2
                for(int j = 0; j < 3; j++) {
                    shortDistance[j] = (float) (oldSpeed0[j] * computationTime + 0.5 * averageAcceleration[j] * computationTime * computationTime);
                }

                for(int j = 0; j < 3; j++) {
                    totalDistance[j] += shortDistance[j];
                }

                //System.arraycopy(totalDistance, 0, totalDistanceCalibrated, 0, 3);

                for(int j = 0; j < 3; j++) {
                    if(totalDistance[j] >= 0) {
                        totalDistanceCalibrated[j] = (float) (totalDistance[j] - (0.5 * EPSILON * totalTime * totalTime));
                        //not += because equation accounts for the fact that error accumulates over time
                    }else totalDistanceCalibrated[j] = (float) (totalDistance[j] + (0.5 * EPSILON * totalTime * totalTime));
                    /*if(totalDistance[j] >= 0) {
                        totalDistanceCalibrated[j] = totalDistance[j] - (0.1208 * totalTime * totalTime - 0.0075 * totalTime + 0.0148);
                        //not += because equation accounts for the fact that error accumulates over time
                    }else totalDistanceCalibrated[j] = totalDistance[j] + (0.1208 * totalTime * totalTime - 0.0075 * totalTime + 0.0148);*/
                }

                    //combinedTotalDistance = triplePythagorean(totalDistance[0], totalDistance[1], totalDistance[2]);


                TV1.setText("Acceleration:\nX: " + averageAcceleration[0] + "\nY: " + averageAcceleration[1] + "\nZ: "
                        + averageAcceleration[2] + "\nDistance this round:\nx: " + shortDistance[0] + "\ny: " + shortDistance[1] + "\nz: "
                        + shortDistance[2] + "\nSpeed this round:\nX: " + speed0[0] + "\nY: " + speed0[1] + "\nZ: " + speed0[2]);

                TV2.setText("Cumulative Calibrated:\nX: " + totalDistanceCalibrated[0] + "\nY: " + totalDistanceCalibrated[1] + "\nZ: "
                        + totalDistanceCalibrated[2] + "\nComputation Time: " + computationTime
                        + "\nTotal Time: " + totalTime);

                //TV1.setText("Accelerations\nX: " + averageAcceleration[0] + "\n" + "Y: " + averageAcceleration[1] + "\n" + "Z: " + averageAcceleration[2]);
                i = 0;


            } else i++;  //end if   i >= NUMBER_OF_POINTS_TO_AVERAGE - 1

            lastUpdateTime = System.currentTimeMillis();

        } //end    if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on)
    } //end function

    private double triplePythagorean(double a, double b, double c) {
        return (Math.sqrt((a * a) + (b * b) + (c * c)));
    }//not used yet

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used, but must be included for this to work
    }
}
