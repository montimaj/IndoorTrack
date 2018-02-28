package app.imu.indoortrack.io;

import android.app.Activity;
import android.content.Context;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;

public class SensorDataWriter {

    private FileOutputStream mOut;

    private static ArrayList<FileOutputStream> sOpenFiles = new ArrayList<>();

    public SensorDataWriter(String fileName, Activity activity) {
        try {
            mOut = activity.openFileOutput(fileName, Context.MODE_PRIVATE);
            sOpenFiles.add(mOut);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void writeData(double x, double y, double z) {
        try {
            Formatter formatx = new Formatter();
            Formatter formaty = new Formatter();
            Formatter formatz = new Formatter();
            formatx.format("%.10f", x);
            formaty.format("%.10f", y);
            formatz.format("%.10f", z);
            String data = System.currentTimeMillis() + "," + formatx + "," + formaty + "," + formatz + "\n";
            mOut.write(data.getBytes());
        } catch (IOException e) { e.printStackTrace(); }
    }

    static void closeAllFiles() {
        try {
            for (FileOutputStream fos: sOpenFiles) {
                fos.close();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
