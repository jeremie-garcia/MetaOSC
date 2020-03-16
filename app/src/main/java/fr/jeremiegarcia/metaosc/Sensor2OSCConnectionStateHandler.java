package fr.jeremiegarcia.metaosc;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import com.illposed.osc.OSCMessage;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Barometer;
import com.mbientlab.metawear.module.Bmm150Magnetometer;
import com.mbientlab.metawear.module.Bmp280Barometer;
import com.mbientlab.metawear.module.Gyro;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Ltr329AmbientLight;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Switch;
import com.mbientlab.metawear.module.Temperature;
import com.mbientlab.metawear.module.Timer;

/**
 * Created by jgarcia on 27/03/16.
 */
public class Sensor2OSCConnectionStateHandler extends MetaWearBoard.ConnectionStateHandler {

    private final static String STATUS = "status";
    private final static String ACC = "acc";
    private final static String GYR = "gyr";
    private final static String MAG = "mag";
    private final static String SWI = "swi";
    private final static String TMP = "tmp";
    private final static String LIG = "lig";
    private final static String BAR = "bar";
    private final static String ALT = "alt";

    private MainActivity mainActivity;
    private MetaWearBoard mwBoard;
    private int id = 0;
    private OSCMessage oscMessage;

    //modules
    private Led ledModule = null;
    private Accelerometer accelModule = null;
    private Gyro gyroModule = null;
    private Bmm150Magnetometer magModule = null;
    private Ltr329AmbientLight ltr329Module = null;
    private Switch switchModule = null;
    private Barometer barometerModule = null;
    private Temperature tempModule = null;

    private static int INIT_BLINK_TIME = 5000;

    public Sensor2OSCConnectionStateHandler() {
        super();
    }

    public Sensor2OSCConnectionStateHandler(MetaWearBoard board, int id, MainActivity activity) {
        this();
        this.mwBoard = board;
        this.id = id;
        this.mainActivity = activity;
    }

    int restart = 1;
    int maxRestart = 10;

    @Override
    public void connected() {
        restart = 1;
        oscMessage = new OSCMessage(getRootOSCAddress());
        oscMessage.addArgument(STATUS);
        oscMessage.addArgument(1);
        OSCManager.sendOscMessage(oscMessage);
        settings();
        initModules();

        //use this method to update the UI (enable components)
        final int boadId = this.id;
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                mainActivity.updateBoardStatus(boadId, true);
            }
        });
    }

    @Override
    public void disconnected() {
        oscMessage = new OSCMessage(getRootOSCAddress());
        oscMessage.addArgument("status");
        oscMessage.addArgument(0);
        OSCManager.sendOscMessage(oscMessage);
        Log.i("Board " + this.id, "Connection Lost");
        final int boadId = this.id;
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                mainActivity.updateBoardStatus(boadId, false);
            }
        });
    }

    @Override
    public void failure(int status, Throwable error) {
        Log.e("Board " + this.id, "Error connecting " + status, error);
        if (restart <= maxRestart) {
            Log.i("Board " + this.id, "Reconnecting attempt " + restart);
            mwBoard.connect();
            restart++;
            final int boadId = this.id;
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.updateBoardStatus(boadId, false);
                }
            });

        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(mainActivity).create();
            alertDialog.setTitle("Board Connection Problem");
            alertDialog.setMessage("Cannot connect to Board: " + this.id + " after " + maxRestart + " attempts\n" +
                    "Check if the board is connected or change the battery and restart the app!");
            alertDialog.show();
        }
    }

    private void setButtonColor(final View btnView, final boolean isDark) {

        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                TypedValue typedValue = new TypedValue();
                Resources.Theme theme = mainActivity.getTheme();
                if (isDark) {
                    theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                } else {
                    theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
                }

                int color = typedValue.data;
                btnView.setBackgroundColor(color);
                commandReceivedFeedbackOnLed();
            }
        });
    }

    private void initModules() {
        initLEDModule();
        initAccelModule();
        initGyroModule();
        initMagnetoModule();
        initLightModule();
        initTempModule();
        initBarometerModule();
        initSwitchModule();

        Log.i("Board " + this.id, "Done initializing sensors");
    }

    private void initSwitchModule() {
        //switch
        try {
            switchModule = mwBoard.getModule(Switch.class);
            Log.i("Board " + this.id, "Switch Module found");
            switchModule.routeData().fromSensor().stream("swi_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("swi_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(SWI);
                            mess.addArgument(message.getData(Boolean.class) ? 1 : 0);
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                }
            });

        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Swith not supported");
        }
    }

    //use 10 first values to remove the offset of the barometer
    private float barometer_offset = 0f;
    private static final int OFFSETCOUNT = 10;

    private void initBarometerModule() {
        //barometer
        try {
            barometerModule = mwBoard.getModule(Barometer.class);
            Log.i("Board " + this.id, "Barometer Module found");


            ((Bmp280Barometer) barometerModule).configure()
                    .setPressureOversampling(Bmp280Barometer.OversamplingMode.STANDARD)
                    .setFilterMode(Bmp280Barometer.FilterMode.AVG_2)
                    .setStandbyTime(Bmp280Barometer.StandbyTime.TIME_0_5)
                    .commit();
            barometerModule.routeData().fromPressure().stream("bar_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("bar_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            Float pressureValue = message.getData(Float.class);

                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(BAR);
                            mess.addArgument(pressureValue);
                            OSCManager.sendOscMessage(mess);
                        }
                    });


                }
            });

            barometerModule.routeData().fromAltitude().stream("alt_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("alt_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();
                        int counter = 1;
                        float sum = 0;

                        @Override
                        public void process(Message message) {
                            Float altitudeValue = message.getData(Float.class);

                            if (counter <= OFFSETCOUNT) {
                                sum += altitudeValue;
                                barometer_offset = sum / counter;
                                counter++;
                            }

                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(ALT);
                            mess.addArgument(altitudeValue - barometer_offset);
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                }
            });

            ((Bmp280Barometer) barometerModule).enableAltitudeSampling();
            barometerModule.start();

        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Barometer not supported");
        }
    }

    //Temperature module use
    private final static int TEMP_SAMPLE_PERIOD = 50;
    private Timer timerModule;

    private void initTempModule() {
        //temperature
        try {
            tempModule = mwBoard.getModule(Temperature.class);
            timerModule = mwBoard.getModule(Timer.class);
            Log.i("Board " + this.id, "Temperature Module found");


            final MultiChannelTemperature.Source onBoard = ((MultiChannelTemperature) tempModule).getSources().get(1);
            ((MultiChannelTemperature) tempModule).routeData().fromSource(onBoard).stream("tmp_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("tmp_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            Float celsius = message.getData(Float.class);
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(TMP);
                            mess.addArgument(celsius);
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                }
            });
            timerModule.scheduleTask(new Timer.Task() {
                @Override
                public void commands() {
                    ((MultiChannelTemperature) tempModule).readTemperature(onBoard);
                }
            }, TEMP_SAMPLE_PERIOD, false).onComplete(new AsyncOperation.CompletionHandler<Timer.Controller>() {
                @Override
                public void success(Timer.Controller result) {
                    result.start();
                }
            });

        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Temperature not supported");
        }
    }

    private void initLightModule() {
        //light
        try {
            ltr329Module = mwBoard.getModule(Ltr329AmbientLight.class);
            Log.i("Board " + this.id, "Light Module found");


            ltr329Module.configure().setGain(Ltr329AmbientLight.Gain.LTR329_GAIN_4X)
                    .setMeasurementRate(Ltr329AmbientLight.MeasurementRate.LTR329_RATE_50MS)
                    .setIntegrationTime(Ltr329AmbientLight.IntegrationTime.LTR329_TIME_50MS)
                    .commit();

            ltr329Module.routeData().fromSensor().stream("lig_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("lig_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {

                            Float lux = message.getData(Long.class) / 1000.f;
                            //Log.i("LIG", message.getData(Long.class).toString());
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(LIG);
                            mess.addArgument(lux);
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                    ltr329Module.start();
                }
            });


        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Light not supported");
        }
    }

    //Magneto Accelero and gyro freq
    private static final float SAMPLING_FREQ = 50.f;

    private void initMagnetoModule() {
        //magneto
        try {
            magModule = mwBoard.getModule(Bmm150Magnetometer.class);
            Log.i("Board " + this.id, "Magneto Module found");

            magModule.setPowerPrsest(Bmm150Magnetometer.PowerPreset.REGULAR);
            magModule.routeData().fromBField().stream("mag_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("mag_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            CartesianFloat axes = message.getData(CartesianFloat.class);
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(MAG);
                            mess.addArgument(axes.x());
                            mess.addArgument(axes.y());
                            mess.addArgument(axes.z());
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                    // enable axis sampling
                    magModule.enableBFieldSampling();
                    magModule.start();

                }
            });


        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Magneto not supported");
        }
    }

    private static final float ANGULAR_RATE = 1000f;

    private void initGyroModule() {
        //gyro
        try {
            gyroModule = mwBoard.getModule(Gyro.class);
            Log.i("Board " + this.id, "Gyro Module found");

            // Set the sampling frequency to 50Hz, or closest valid ODR
            gyroModule.setOutputDataRate(SAMPLING_FREQ);
            // Set the measurement range to +/- 4g, or closet valid range
            gyroModule.setAngularRateRange(ANGULAR_RATE);

            gyroModule.routeData().fromAxes().stream("gyro_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("gyro_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            CartesianFloat axes = message.getData(CartesianFloat.class);
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(GYR);
                            mess.addArgument(axes.x());
                            mess.addArgument(axes.y());
                            mess.addArgument(axes.z());
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                    // Switch the gyro to active mode
                    gyroModule.start();

                }
            });


        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Gyri not supported");
        }
    }

    private static final float AXIS_SAMPLING_RANGE = 8.0f;

    private void initAccelModule() {
        //accel
        try {
            accelModule = mwBoard.getModule(Accelerometer.class);

            // enable axis sampling
            // Set the sampling frequency to 50Hz, or closest valid ODR
            accelModule.setOutputDataRate(SAMPLING_FREQ);
            // Set the measurement range to +/- 4g, or closet valid range
            accelModule.setAxisSamplingRange(AXIS_SAMPLING_RANGE);

            accelModule.routeData().fromAxes().stream("accel_stream_key").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                @Override
                public void success(RouteManager result) {
                    result.subscribe("accel_stream_key", new RouteManager.MessageHandler() {

                        OSCMessage mess = new OSCMessage();

                        @Override
                        public void process(Message message) {
                            CartesianFloat axes = message.getData(CartesianFloat.class);
                            mess = new OSCMessage();
                            mess.setAddress(getRootOSCAddress());
                            mess.addArgument(ACC);
                            mess.addArgument(axes.x());
                            mess.addArgument(axes.y());
                            mess.addArgument(axes.z());
                            OSCManager.sendOscMessage(mess);
                        }
                    });
                    accelModule.enableAxisSampling();
                    accelModule.start();

                }
            });

        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "Accel not supported");
        }
    }

    private void initLEDModule() {
        //LED
        try {
            ledModule = mwBoard.getModule(Led.class);
            Log.i("Board " + this.id, "LED Module found");
            //use the led to play a pattern that will be stopped at the end
            ledModule.play(false);
            ledModule.configureColorChannel(id == 1 ? Led.ColorChannel.GREEN : Led.ColorChannel.BLUE)
                    .setRiseTime((short) 0).setPulseDuration((short) 200)
                    .setRepeatCount((byte) (INIT_BLINK_TIME / 200)).setHighTime((short) 100)
                    .setHighIntensity((byte) 31).setLowIntensity((byte) 0)
                    .commit();
            ledModule.play(true);

        } catch (UnsupportedModuleException e) {
            Log.e("Board " + this.id, "LED not supported");
        }
    }

    private void settings() {
        //to get within scope of newt anonymous function
        final int boardId = this.id;
        mwBoard.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
            @Override
            public void success(Byte result) {
                final int percentage = result;

                mainActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mainActivity.updateBoardBattery(boardId, percentage);
                    }
                });
            }

            @Override
            public void failure(Throwable error) {
                Log.e("Board " + boardId, "Battery problem");
            }
        });

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            public void run() {
                Log.e("Board " + boardId, "Battery check");
                settings();
            }
        }, 1000 * 60 * 5); //every 5 minutes
    }

    public String getRootOSCAddress() {
        return "/mwb/" + id;
    }

    public void commandReceivedFeedbackOnLed() {
        if (ledModule != null) {
            ledModule.play(false);
            ledModule.configureColorChannel(id == 1 ? Led.ColorChannel.GREEN : Led.ColorChannel.BLUE)
                    .setRiseTime((short) 0).setPulseDuration((short) 200)
                    .setRepeatCount((byte) (1000 / 200)).setHighTime((short) 100)
                    .setHighIntensity((byte) 31).setLowIntensity((byte) 0)
                    .commit();
            ledModule.play(true);
        }
    }
}