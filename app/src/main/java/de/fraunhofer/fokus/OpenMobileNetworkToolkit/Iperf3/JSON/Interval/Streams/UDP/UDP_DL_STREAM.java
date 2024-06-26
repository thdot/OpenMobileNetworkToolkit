package de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Streams.UDP;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Streams.STREAM_TYPE;
import org.json.JSONException;
import org.json.JSONObject;

public class UDP_DL_STREAM extends UDP_STREAM {
    private double jitter_ms;
    private int lost_packets;
    private int packets;
    private double lost_percent;
    public UDP_DL_STREAM(){
        super();
    }
    public void parse(JSONObject data) throws JSONException {
        super.parse(data);
        this.jitter_ms = data.getDouble("jitter_ms");
        this.lost_packets = data.getInt("lost_packets");
        this.packets = data.getInt("packets");
        this.lost_percent = data.getDouble("lost_percent");
        this.setStreamType(STREAM_TYPE.UDP_DL);
    }
    public double getJitter_ms() {
        return jitter_ms;
    }
    public int getLost_packets() {
        return lost_packets;
    }
    public int getPackets() {
        return packets;
    }
    public double getLost_percent() {
        return lost_percent;
    }

}
