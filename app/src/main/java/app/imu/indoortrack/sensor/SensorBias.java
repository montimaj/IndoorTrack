package app.imu.indoortrack.sensor;

import java.util.Vector;

public class SensorBias {

    private Vector<Double> mVecX;
    private Vector<Double> mVecY;
    private Vector<Double> mVecZ;

    public SensorBias(Vector<Double> vecx, Vector<Double> vecy, Vector<Double> vecz) {
        mVecX = vecx;
        mVecY = vecy;
        mVecZ = vecz;
    }

    private double getStandardDeviation(Vector<Double> v) {
        double sum = 0;
        for( double values: v) {
            sum += values;
        }
        int n = v.size();
        double avg = sum/n;
        sum = 0;
        for (double values: v) {
            sum += Math.pow(values - avg, 2);
        }
        return Math.sqrt(sum/(n-1));
    }

    public double getBiasX() {
        return getStandardDeviation(mVecX);
    }

    public double getBiasY() {
        return getStandardDeviation(mVecY);
    }

    public double getBiasZ() {
        return getStandardDeviation(mVecZ);
    }
}
