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

public class DataFilter {

    // discrete time interval
    private double dt;
    // position measurement noise (meter)
    private double measurementNoise;
    // acceleration noise (meter/sec^2)
    private double accelerationNoise;

    private RealMatrix A;
    private RealMatrix B;
    private RealVector x;
    private KalmanFilter kFilter;

    public DataFilter() {
        this.dt = 0.1d;
        this.measurementNoise = 10d;
        this.accelerationNoise = 0.2d;
        this.x = new ArrayRealVector(new double[] { 0, 0 });
        initialize();
    }

    public DataFilter(double dt, double m_noise, double acc_noise, RealVector x0) {
        this.dt = dt;
        this.measurementNoise = m_noise;
        this.accelerationNoise = acc_noise;
        this.x = x0;
        initialize();
    }

    private void initialize() {
        this.A = new Array2DRowRealMatrix(new double[][] { { 1, dt }, { 0, 1 } });
        this.B = new Array2DRowRealMatrix(new double[][] { { Math.pow(dt, 2d) / 2d }, { dt } });
        RealMatrix h = new Array2DRowRealMatrix(new double[][]{{1d, 0d}});
        RealMatrix q = new Array2DRowRealMatrix(new double[][]{
                {Math.pow(dt, 4d) / 4d, Math.pow(dt, 3d) / 2d},
                {Math.pow(dt, 3d) / 2d, Math.pow(dt, 2d)}});
        RealMatrix q1 = q.scalarMultiply(Math.pow(accelerationNoise, 2));
        RealMatrix p0 = new Array2DRowRealMatrix(new double[][]{{1, 1}, {1, 1}});
        RealMatrix r = new Array2DRowRealMatrix(new double[]{Math.pow(measurementNoise, 2)});
        ProcessModel pm = new DefaultProcessModel(A, B, q1, x, p0);
        MeasurementModel mm = new DefaultMeasurementModel(h, r);
        this.kFilter = new KalmanFilter(pm, mm);
    }
    /* u => Accelerometer Reading; z => GPS Reading */
    public RealVector estimate(RealVector u, RealVector z) {
        x = (A.operate(x)).add(B.operate(u));
        kFilter.predict(u);
        kFilter.correct(z);
        return kFilter.getStateEstimationVector();
    }
}
