/*
 *  SPDX-FileCopyrightText: 2023 Peter Hasse <peter.hasse@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2023 Johann Hackler <johann.hackler@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2023 Fraunhofer FOKUS
 *
 *  SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package de.fraunhofer.fokus.OpenMobileNetworkToolkit.InfluxDB2x;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import com.influxdb.client.domain.OnboardingRequest;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.client.write.events.BackpressureEvent;
import com.influxdb.client.write.events.WriteRetriableErrorEvent;
import com.influxdb.client.write.events.WriteSuccessEvent;

import java.io.IOException;
import java.util.List;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;


public class InfluxdbConnection {
    private final static String TAG = "InfluxDBConnection";
    SharedPreferences sp;
    private final char[] token;
    private final String org;
    private final String bucket;
    private final String url;
    private final Context context;
    private InfluxDBClient influxDBClient;
    private WriteApi writeApi;
    private GlobalVars gv;

    public InfluxdbConnection(String URL, String token, String org, String bucket,
                              Context context) {
        this.token = token.toCharArray();
        this.org = org;
        this.url = URL;
        this.bucket = bucket;
        this.context = context;
        this.gv = GlobalVars.getInstance();
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        influxDBClient = InfluxDBClientFactory.create(this.url, this.token, this.org, this.bucket);
        influxDBClient.enableGzip(); // maybe we want a setting for this?
    }

    /**
     * Open the write API on the InfluxConnection
     */
    public void open_write_api() {
        if (writeApi != null) return;
        try {
            //influxDBClient = InfluxDBClientFactory.create(url, this.token, org, bucket);
            writeApi = influxDBClient.makeWriteApi(WriteOptions.builder()
                .batchSize(1000)
                .flushInterval(1000)
                .backpressureStrategy(BackpressureOverflowStrategy.DROP_OLDEST)
                .bufferLimit(100000)
                .jitterInterval(10)
                .retryInterval(500)
                .exponentialBase(4)
                .build());
            writeApi.listenEvents(BackpressureEvent.class, value -> {
                Log.d(TAG, "Backpressure: Reason: " + value.getReason());
                value.logEvent();
            });
            writeApi.listenEvents(WriteSuccessEvent.class, value -> {
                if ( sp.getBoolean("enable_influx", false)) {
                    gv.getLog_status().setColorFilter(Color.argb(255, 0, 255, 0));
                }
            });
            writeApi.listenEvents(WriteRetriableErrorEvent.class, value -> {
                value.logEvent();
                if ( sp.getBoolean("enable_influx", false)) {
                    gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
                }
            });
        } catch (com.influxdb.exceptions.InfluxException e) {
            Log.d(TAG, "connect: Can't connect to InfluxDB");
            e.printStackTrace();
        }
    }

    /**
     * Disconnect and destroy the client
     */
    public void disconnect() {
        // make sure we a instance of the client. This can happen on an app resume
        if (influxDBClient != null) {
            Log.d(TAG, "disconnect: Flushing Influx write API if possible");
            flush();
            try {
                Log.d(TAG, "disconnect: Closing Influx write API");
                writeApi.close();
                writeApi = null;
            } catch (com.influxdb.exceptions.InfluxException e) {
                Log.d(TAG, "disconnect: Error while closing write API");
                e.printStackTrace();
            }
            try {
                Log.d(TAG, "disconnect: Closing influx connection");
                influxDBClient.close();
                influxDBClient = null;
            } catch (com.influxdb.exceptions.InfluxException e) {
                Log.d(TAG, "disconnect: Error while closing influx connection");
                e.printStackTrace();
            }
        } else {
            Log.d(TAG, "disconnect() was called on not existing instance of the influx client");
        }
    }

    /**
     * Add a point to the message queue
     */
    public boolean writePoint(Point point) throws IOException {
        if (influxDBClient != null && influxDBClient.ping()) {
            try {
                writeApi.writePoint(point);
            } catch (com.influxdb.exceptions.InfluxException e) {
                Log.d(TAG, "writePoint: Error while writing points to influx DB");
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            Log.d(TAG, "writePoint: InfluxDB not reachable");
            return false;
        }
    }

    /**
     * Write string records to the queue
     * @param points String list of records
     * @return not yet useful
     * @throws IOException
     */
    public boolean writeRecords(List<String> points) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (influxDBClient != null && influxDBClient.ping()) {
                        try {
                            writeApi.writeRecords(WritePrecision.MS, points);
                        } catch (com.influxdb.exceptions.InfluxException e) {
                            Log.d(TAG, "writeRecords: Error while writing points to influx DB");
                            e.printStackTrace();
                        }
                    } else {
                        Log.d(TAG, "writeRecords: InfluxDB not reachable: " + url);
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        return true;
    }

    /**
     *
     * @param points
     * @return
     * @throws IOException
     */
    public boolean writePoints(List<Point> points) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (influxDBClient != null && influxDBClient.ping()) {
                        try {
                            writeApi.writePoints(points);
                        } catch (com.influxdb.exceptions.InfluxException e) {
                            Log.d(TAG, "writePoint: Error while writing points to influx DB");
                            e.printStackTrace();
                        }
                    } else {
                        Log.d(TAG, "writePoints: InfluxDB not reachable: " + url);
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        return true;
    }

    /**
     * Onboard a influxDB and store credentials
     * @return
     */
    public boolean onboard() {
        try {
            if (influxDBClient.isOnboardingAllowed()) {
                OnboardingRequest or = new OnboardingRequest();
                or.bucket("omnt");
                or.org("OMNT");
                or.password("omnt2022"); //todo THIS SHOULD NOT BE HARDCODED
                or.username("omnt");
                or.token("1234567890"); //todo generate a token
                influxDBClient.onBoarding(or);
                Log.d(TAG, "setup: Database onboarding successfully");
                return true;
            } else {
                Log.d(TAG, "setup: Database was already onboarded");
                return false;
            }
        } catch (com.influxdb.exceptions.InfluxException e) {
            return false;
        }
    }

    /**
     * If we can reach the influxDB call flush on the write API
     * @return
     */
    // todo handle unreachable DB
    public boolean flush() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (influxDBClient.ping()) {
                        writeApi.flush();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        return true;
    }

    public WriteApi getWriteApi() {
        return writeApi;
    }

    public boolean ping() {
        return influxDBClient.ping();
    }
}

