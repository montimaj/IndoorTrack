package app.imu.indoortrack.sensor;

import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.filter.MeasurementModel;
import org.apache.commons.math3.filter.ProcessModel;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class SensorDataFilter {

    private RealMatrix mA;
    private RealMatrix mB;
    private RealVector mX;
    private KalmanFilter mKFilter;

    private static final double DT = 1d;
    private static final double GPS_NOISE = 0.04d;
    private static final double ACC_NOISE = 0.03d;

    public SensorDataFilter(double gpsValue) {
        mX = new ArrayRealVector(new double[] { gpsValue, 0 });
        mA = new Array2DRowRealMatrix(new double[][] { { 1, DT }, { 0, 1 } });
        mB = new Array2DRowRealMatrix(new double[][] { { Math.pow(DT, 2d) / 2d }, { DT } });
        RealMatrix h = new Array2DRowRealMatrix(new double[][]{{1d, 0d}});
        RealMatrix q = new Array2DRowRealMatrix(new double[][]{
                {Math.pow(DT, 4d) / 4d, Math.pow(DT, 3d) / 2d},
                {Math.pow(DT, 3d) / 2d, Math.pow(DT, 2d)}});
        RealMatrix q1 = q.scalarMultiply(Math.pow(ACC_NOISE, 2));
        RealMatrix p0 = new Array2DRowRealMatrix(new double[][]{{1, 1}, {1, 1}});
        RealMatrix r = new Array2DRowRealMatrix(new double[]{Math.pow(GPS_NOISE, 2)});
        ProcessModel pm = new DefaultProcessModel(mA, mB, q1, mX, p0);
        MeasurementModel mm = new DefaultMeasurementModel(h, r);
        mKFilter = new KalmanFilter(pm, mm);
    }

    /**
     *
     * @param u Acceleration Reading
     * @param z GPS Reading
     * @return Corrected vector
     */
    public RealVector estimate(RealVector u, RealVector z) {
        mX = (mA.operate(mX)).add(mB.operate(u));
        mKFilter.predict(u);
        mKFilter.correct(z);
        return mKFilter.getStateEstimationVector();
    }
}
