package uk.org.openseizuredetector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SdAnalyserTest {
    public SdAnalyser sda;

    public String alarmJSON = "{ 'dataType': 'raw', " +
            "'data': [" +
            "1644, 1316, 1144, 1332, 1716, 1716, 1392, 1148, 1276, 1660, " +
            "1716, 1496, 1196, 1232, 1572, 1684, 1552, 1236, 1228, 1528, " +
            "1648, 1572, 1268, 1208, 1492, 1680, 1596, 1272, 1192, 1424, " +
            "1668, 1636, 1300, 1200, 1356, 1652, 1684, 1420, 1208, 1304, " +
            "1620, 1672, 1448, 1232, 1248, 1536, 1676, 1540, 1256, 1244, " +
            "1544, 1644, 1512, 1252, 1236, 1504, 1684, 1540, 1252, 1200, " +
            "1436, 1664, 1624, 1344, 1204, 1396, 1616, 1596, 1344, 1216, " +
            "1368, 1648, 1660, 1388, 1220, 1316, 1588, 1672, 1460, 1232, " +
            "1256, 1580, 1672, 1500, 1256, 1288, 1540, 1688, 1516, 1252, " +
            "1212, 1464, 1684, 1584, 1288, 1224, 1468, 1692, 1616, 1316, " +
            "1188, 1360, 1680, 1724, 1424, 1192, 1224, 1556, 1744, 1588, " +
            "1260, 1220, 1472, 1692, 1608, 1328, 1192, 1412, 1668, 1656, " +
            "1356, 1216, 1304, 1636, 1712], " +
            "'HR':54, " +
            "'Mute':0 " +
            "}";

    private String okJSON = "{ " +
            "\"dataType\": \"raw\", " +
            "\"data\": [" +
            "1140, 1188, 1144, 1172, 1228, 1212, 1236, 1236, 1256, 1320, " +
            "1316, 1280, 1240, 1280, 1324, 1284, 1292, 1268, 1284, 1276, " +
            "1296, 1324, 1308, 1288, 1304, 1276, 1304, 1304, 1276, 1296, " +
            "1280, 1284, 1296, 1300, 1284, 1288, 1296, 1284, 1300, 1280, " +
            "1300, 1292, 1276, 1304, 1276, 1316, 1280, 1288, 1296, 1280, " +
            "1284, 1272, 1300, 1284, 1288, 1292, 1276, 1296, 1276, 1292, " +
            "1280, 1284, 1284, 1284, 1284, 1284, 1288, 1284, 1304, 1284, " +
            "1288, 1280, 1296, 1284, 1292, 1296, 1280, 1276, 1288, 1296, " +
            "1276, 1292, 1288, 1276, 1288, 1276, 1272, 1272, 1292, 1284, " +
            "1292, 1288, 1280, 1284, 1284, 1268, 1288, 1268, 1276, 1300, " +
            "1268, 1292, 1292, 1304, 1288, 1284, 1280, 1276, 1288, 1280, " +
            "1300, 1288, 1320, 1268, 1288, 1280, 1304, 1280, 1280, 1288, " +
            "1292, 1308, 1268, 1292, 1280], " +
            "\"HR\":57, " +
            "\"Mute\":0 " +
            "}";

    @Before
    public void setUp() throws Exception {
        sda = new SdAnalyser(25.0,
                3.0,
                8.0,
                5.0,
                5.0,
                100.0,
                54.0
        );
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testFreq2fftBin() {
        int n;
        n = sda.freq2fftBin(0.0);
        assertEquals(0,n);
        n = sda.freq2fftBin(5.0);
        assertEquals(25,n);
    }

    @Test
    public void testGetMagnitude() {
        double[] fft = {1, 1,
                        2, 1,
                        2, 2};
        double m;
        m = sda.getMagnitude(fft,0);
        assertEquals(2.0, m, m * 1e-4);
        m = sda.getMagnitude(fft,1);
        assertEquals(5.0, m, m * 1e-4);
        m = sda.getMagnitude(fft,2);
        assertEquals(8.0, m, m * 1e-4);
    }

    @Test
    public void testGetAccelDataFromJson() {
        double[] retVal;

        retVal = sda.getAccelDataFromJSON(okJSON);
        assertNotNull(retVal);
        assertEquals(125,retVal.length);
        assertEquals(1140,retVal[0],0.001);
        assertEquals(1280,retVal[124],0.001);
    }


    @Test
    public void testGetSpectrumRatio() {
        double[] okRawVals;
        double[] alarmRawVals;
        okRawVals = sda.getAccelDataFromJSON(okJSON);
        alarmRawVals = sda.getAccelDataFromJSON(alarmJSON);

        double okRatio;
        double alarmRatio;

        okRatio = sda.getSpectrumRatio(okRawVals);
        alarmRatio = sda.getSpectrumRatio(alarmRawVals);

        assertTrue("Check Spectrum Ratio for OK data "+okRatio+" is <="+sda.mAlarmRatioThresh,
                okRatio <= sda.mAlarmRatioThresh);
        assertTrue("Check Spectrum Ratio for ALARM data "+alarmRatio+" is >"+sda.mAlarmRatioThresh,
                alarmRatio > sda.mAlarmRatioThresh);

    }


    @Test
    public void testgetAlarmState() {
        double[] okRawVals;
        double[] alarmRawVals;
        okRawVals = sda.getAccelDataFromJSON(okJSON);
        alarmRawVals = sda.getAccelDataFromJSON(alarmJSON);

        int okAlarmState;
        int alarmAlarmState;

        okAlarmState = sda.getAlarmState(okRawVals);
        alarmAlarmState = sda.getAlarmState(alarmRawVals);

        assertEquals("check OK start detected from raw data",0, okAlarmState);
        assertEquals("check alarm state detected from raw data",1, alarmAlarmState);
    }

}