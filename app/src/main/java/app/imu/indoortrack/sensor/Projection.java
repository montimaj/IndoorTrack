package app.imu.indoortrack.sensor;

public class Projection {
    private static final double R = 6378137; // radius
    private static final double E = 8.1819190842622e-2;  // eccentricity

    private static final double ASQ = Math.pow(R,2);
    private static final double ESQ = Math.pow(E,2);

    public static double[] cartesianToGeodetic(double x, double y, double z) {
        double b = Math.sqrt( ASQ * (1-ESQ) );
        double bsq = Math.pow(b,2);
        double ep = Math.sqrt( (ASQ - bsq)/bsq);
        double p = Math.sqrt( Math.pow(x,2) + Math.pow(y,2) );
        double th = Math.atan2(R*z, b*p);
        double lon = Math.atan2(y,x);
        double lat = Math.atan2( (z + Math.pow(ep,2)*b*Math.pow(Math.sin(th),3) ), (p - ESQ*R*Math.pow(Math.cos(th),3)) );
        double N = R/( Math.sqrt(1-ESQ*Math.pow(Math.sin(lat),2)) );
        double alt = p / Math.cos(lat) - N;
        lon = lon % (2*Math.PI);
        return new double[] {Math.toDegrees(lat), Math.toDegrees(lon), alt};
    }


    public static double[] geodeticToCartesian(double lat, double lon, double alt) {
        lat = Math.toRadians(lat);
        lon = Math.toRadians(lon);
        double N = R / Math.sqrt(1 - ESQ * Math.pow(Math.sin(lat),2));
        double x = (N+alt) * Math.cos(lat) * Math.cos(lon);
        double y = (N+alt) * Math.cos(lat) * Math.sin(lon);
        double z = ((1-ESQ) * N + alt) * Math.sin(lat);
        return new double[] {x, y, z};
    }
}
