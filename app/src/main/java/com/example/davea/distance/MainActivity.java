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

    //variables:
    public float averageAcceleration[] = new float[3];
    public int i = 0;
    public boolean on = true;
    public float shortDistance[] = new float[3];
    public float totalDistance[] = new float[3];
    public float totalDistanceCalibrated[] = new float[3];
    public float combinedTotalDistance = 0;
    public float speed0[] = new float[3];
    public float oldSpeed0[] = new float[3];
    public double startTime = 0;
    public double lastUpdateTime = 0;
    public double computationTime = 0;
    public double totalTime = 0;
    public int k = 0;
    public double startTime2 = 0;
    public double initialTime = 0;
    public boolean resetted = false;
    public double pausedTime = 0;

    //sensors:
    public Sensor accelerometer;
    public SensorManager accelSensorManager;
    public SensorManager LASensorManager;
    public Sensor LASensor;

    //constants:
    final public int INTERVAL = 75;
    //kfinal public int NUMBER_OF_POINTS_TO_AVERAGE = 3;
    final public float EPSILON = (float) 0.1;    //Adwaya's epsilon: 0.1594
    //public float Y_AXIS_CORRECTION = (float) -0.24;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setup();    //assign and setup everything


        BtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //toggle on and off
                if(on){
                    pausedTime = System.currentTimeMillis();
                }
                else{
                    initialTime += (System.currentTimeMillis() - pausedTime);   //prevents totalTime from increasing while paused
                }
                on = !on;
                //i = 0;
                if(resetted) {
                    initialTime = System.currentTimeMillis();
                    resetted = false;
                }
            }
        });

        BtnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //must click reset button twice to clear screen and reset data
                if(!on) {
                    TV1.setText("");
                    reset();
                    resetted = true;
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
            accelSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            assert accelSensorManager != null;   //ensures next line does not return null pointer exception
            accelerometer = accelSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            accelSensorManager.registerListener(this, accelerometer, accelSensorManager.SENSOR_DELAY_NORMAL);
            //if deice not not have LA sensor, warn user:
            Toast.makeText(this, "REVERTING TO ACCELEROMETER.\nACCURACY REDUCED.\nUSE FOR TESTING PURPOSES ONLY.", Toast.LENGTH_LONG).show();
        }


        //buttons:
        BtnStart = findViewById(R.id.Start);
        BtnClear = findViewById(R.id.Clear);
        //textViews:
        TV1 = findViewById(R.id.TV1);

        initialTime = System.currentTimeMillis();

        reset();
    }

    public void reset(){
        //reset everything to 0
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
            k = 0;
            pausedTime = 0;
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;

        if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on) {

            //used for determining the time it took from collecting data to finding the final average of the desired number of data points
            if (i == 0) startTime = System.currentTimeMillis();

            //sum up acceleration values
            //not using for loop in order to increase efficiency and decrease execution time
            averageAcceleration[0] = (averageAcceleration[0] * i + event.values[0]) / (i + 1);
            averageAcceleration[1] = (averageAcceleration[1] * i + event.values[1]) / (i + 1);
            averageAcceleration[2] = (averageAcceleration[2] * i + event.values[2]) / (i + 1);

            totalTime = (System.currentTimeMillis() - initialTime) / 1000;

                //Now, get distance: dx = v0 * t + 0.5 * a * t^2
            totalDistance[0] = (float) (0.5 * averageAcceleration[0] * totalTime * totalTime);
            totalDistance[1] = (float) (0.5 * averageAcceleration[1] * totalTime * totalTime);
            totalDistance[2] = (float) (0.5 * averageAcceleration[2] * totalTime * totalTime);

            combinedTotalDistance = (float) triplePythagorean(totalDistance[0], totalDistance[1], totalDistance[2]);

            if(sensor.getType() == Sensor.TYPE_ACCELEROMETER) { //if using just plain accelerometer, I want to account for error on each independent axis
                for (int j = 0; j < 3; j++) {
                    if (totalDistance[j] >= 0) {
                        totalDistanceCalibrated[j] = (float) (totalDistance[j] - (0.5 * EPSILON * totalTime * totalTime));
                        //not += because totalDistance accumulates itself
                    } else
                        totalDistanceCalibrated[j] = (float) (totalDistance[j] + (0.5 * EPSILON * totalTime * totalTime));
                }
            }

            else if(sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){   //else if using LA just account for error in combined value since gravity will not be in there
                //account for error:
                if (combinedTotalDistance >= 0) {
                    combinedTotalDistance -= (0.5 * EPSILON * totalTime * totalTime);
                    //not += because equation accounts for the fact that error accumulates over time
                } else
                    combinedTotalDistance += (0.5 * EPSILON * totalTime * totalTime);
            }

                TV1.setText("Acceleration:\nX: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: "
                        + event.values[2] + "\n\n\nCumulative Calibrated:\nX: " + totalDistanceCalibrated[0] + "\nY: " + totalDistanceCalibrated[1] + "\nZ: "
                        + totalDistanceCalibrated[2] + "\nTotal Time: " + totalTime + "\n Total Combined Distance: " + combinedTotalDistance);

                i++;

                totalTime += (System.currentTimeMillis() - startTime2) / 1000;
                startTime2 = System.currentTimeMillis();


            lastUpdateTime = System.currentTimeMillis();

        } //end    if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on)
    } //end function*/









/*
    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;

        if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on) {

            //used for determining the time it took from collecting data to finding the final average of the desired number of data points
            if (i == 0) startTime = System.currentTimeMillis();

            //sum up acceleration values
            //not using for loop in order to increase efficiency and decrease execution time
            averageAcceleration[0] += event.values[0];//= (averageAcceleration[0] * i + event.values[0]) / (i + 1);
            averageAcceleration[1] += event.values[1]; //= (averageAcceleration[1] * i + event.values[1]) / (i + 1);
            averageAcceleration[2] += event.values[2]; //= (averageAcceleration[2] * i + event.values[2]) / (i + 1);

            if (i >= NUMBER_OF_POINTS_TO_AVERAGE - 1) {
                //take the average of the acceleration value sums
                averageAcceleration[0] /= NUMBER_OF_POINTS_TO_AVERAGE;
                averageAcceleration[1] /= NUMBER_OF_POINTS_TO_AVERAGE;
                averageAcceleration[2] /= NUMBER_OF_POINTS_TO_AVERAGE;

                //computationTime should be about the same as TIME_INTERVAL, but using computationTime ensures that the true time is used, esp. for slower machines
                computationTime = (System.currentTimeMillis() - startTime + INTERVAL) / (long)1000;
                totalTime += computationTime;   //total time program has been running
                startTime2 = System.currentTimeMillis();


                System.arraycopy(speed0, 0, oldSpeed0, 0, 3); //oldSpeed0 = speed0

                //set speed (future v0) every computationTime seconds ~ TIME_INTERVAL
                speed0[0] += averageAcceleration[0] * computationTime; //from: a * t = v - v0
                speed0[1] += averageAcceleration[1] * computationTime;
                speed0[2] += averageAcceleration[2] * computationTime;

                //Now, get distance: dx = v0 * t + 0.5 * a * t^2
                shortDistance[0] = (float) (oldSpeed0[0] * computationTime + 0.5 * averageAcceleration[0] * computationTime * computationTime);
                shortDistance[1] = (float) (oldSpeed0[1] * computationTime + 0.5 * averageAcceleration[1] * computationTime * computationTime);
                shortDistance[2] = (float) (oldSpeed0[2] * computationTime + 0.5 * averageAcceleration[2] * computationTime * computationTime);

                //short distance is the distance traveled in this time period
                totalDistance[0] += shortDistance[0];
                totalDistance[1] += shortDistance[1];
                totalDistance[2] += shortDistance[2];

                totalTime += (System.currentTimeMillis() - startTime2) / 1000;
                startTime2 = System.currentTimeMillis();

                combinedTotalDistance = (float) triplePythagorean(totalDistance[0], totalDistance[1], totalDistance[2]);

                if(sensor.getType() == Sensor.TYPE_ACCELEROMETER) { //if using just plain accelerometer, I want to account for error on each independent axis
                    for (int j = 0; j < 3; j++) {
                        if (totalDistance[j] >= 0) {
                            totalDistanceCalibrated[j] = (float) (totalDistance[j] - (0.5 * EPSILON * totalTime * totalTime));
                            //not += because totalDistance accumulates itself
                        } else
                            totalDistanceCalibrated[j] = (float) (totalDistance[j] + (0.5 * EPSILON * totalTime * totalTime));
                    }
                }
                else if(sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){   //else if using LA just account for error in combined value since gravity will not be in there
                    //account for error:
                    if (combinedTotalDistance >= 0) {
                        combinedTotalDistance -= (0.5 * EPSILON * totalTime * totalTime);
                        //not += because equation accounts for the fact that error accumulates over time
                    } else
                        combinedTotalDistance += (0.5 * EPSILON * totalTime * totalTime);
                }

                totalTime += (System.currentTimeMillis() - startTime2) / 1000;
                startTime2 = System.currentTimeMillis();

                TV1.setText("Acceleration:\nX: " + averageAcceleration[0] + "\nY: " + averageAcceleration[1] + "\nZ: "
                        + averageAcceleration[2] + "\nDistance this round:\nx: " + shortDistance[0] + "\ny: " + shortDistance[1] + "\nz: "
                        + shortDistance[2] + "\nSpeed this round:\nX: " + speed0[0] + "\nY: " + speed0[1] + "\nZ: " + speed0[2]);

                TV2.setText("Cumulative Calibrated:\nX: " + totalDistanceCalibrated[0] + "\nY: " + totalDistanceCalibrated[1] + "\nZ: "
                        + totalDistanceCalibrated[2] + "\nComputation Time: " + computationTime
                        + "\nTotal Time: " + totalTime + "\n Total Combined Distance: " + combinedTotalDistance);

                i = 0;

                totalTime += (System.currentTimeMillis() - startTime2) / 1000;
                startTime2 = System.currentTimeMillis();

            } else i++;  //end if   i >= NUMBER_OF_POINTS_TO_AVERAGE - 1

            lastUpdateTime = System.currentTimeMillis();

        } //end    if ((System.currentTimeMillis() - lastUpdateTime >= INTERVAL) && on)
    } //end function*/


    private double triplePythagorean(double a, double b, double c) {
        return (Math.sqrt((a * a) + (b * b) + (c * c)));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used, but must be included for this to work
    }



/*    @Override
    public void onSensorChanged(SensorEvent event) {

        if((k < NUMBER_OF_POINTS_TO_AVERAGE) && done && on) {
            if(k == 0) {
                beginTime = System.currentTimeMillis();
            }

            for (int l = 0; l < 3; l++) {
                averageAcceleration[l] = (((averageAcceleration[l] * i) + event.values[l]) - Y_AXIS_CORRECTION) / (i + 1);
            }
            if(k == NUMBER_OF_POINTS_TO_AVERAGE - 1){
                timeLength = System.currentTimeMillis() - beginTime;
            }
            k++;
        }
        else if (done && on){
            done = false;
            k = 0;

            //computationTime should be about the same as TIME_INTERVAL, but using computationTime ensures that the true time is used, esp. for slower machines
            //computationTime = (System.currentTimeMillis() - startTime) / (long) 1000;
            totalTime += timeLength;

            //oldSpeed0 = speed0:
            System.arraycopy(speed0, 0, oldSpeed0, 0, 3);

            //set speed (future v0) every computationTime seconds ~ TIME_INTERVAL
            for (int j = 0; j < 3; j++) {
                speed0[j] += averageAcceleration[j] * (timeLength); //from: a * t = v - v0
             }

            //Now, get distance:
            //dx = v0 * t + 0.5 * a * t^2
            for (int j = 0; j < 3; j++) {
                shortDistance[j] = (float) (oldSpeed0[j] * timeLength + 0.5 * averageAcceleration[j] * timeLength * timeLength);
            }

            for (int j = 0; j < 3; j++) {
                totalDistance[j] += shortDistance[j];
            }

            for (int j = 0; j < 3; j++) {
                if (totalDistance[j] >= 0) {
                    totalDistanceCalibrated[j] = (float) (totalDistance[j] - (0.5 * EPSILON * totalTime * totalTime));
                    //not += because equation accounts for the fact that error accumulates over time
                } else
                    totalDistanceCalibrated[j] = (float) (totalDistance[j] + (0.5 * EPSILON * totalTime * totalTime));

            }

            //combinedTotalDistance = triplePythagorean(totalDistance[0], totalDistance[1], totalDistance[2]);


            TV1.setText("Acceleration:\nX: " + averageAcceleration[0] + "\nY: " + averageAcceleration[1] + "\nZ: "
                    + averageAcceleration[2] + "\nUncalibrated Distance:\nx: " + totalDistance[0] + "\ny: " + totalDistance[1] + "\nz: "
                    + totalDistance[2] + "\nSpeed this round:\nX: " + speed0[0] + "\nY: " + speed0[1] + "\nZ: " + speed0[2]);

/*            TV2.setText("Cumulative Calibrated:\nX: " + totalDistanceCalibrated[0] + "\nY: " + totalDistanceCalibrated[1] + "\nZ: "
                    + totalDistanceCalibrated[2] + "\nComputation Time: " + computationTime
                    + "\nTotal Time: " + totalTime);


            done = true;
        }
    }


*/

}