package ocss.nmea.parser;

import java.text.SimpleDateFormat;

import java.util.Date;

import java.util.TimeZone;

import static org.junit.Assert.*;
import org.junit.Test;

public class StringParsersTest
{
  public StringParsersTest()
  {
  }

  /**
   * @see StringParsers#parseRMC(String)
   */
  @Test
  public void testParseRMC()
  {
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    sdf.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));

    String str = "$IIRMC,220526.00,A,3754.34,N,12223.20,W,3.90,250,,015,E,N*07";

    RMC rmc = StringParsers.parseRMC(str);
//  Date date = rmc.getRmcDate();
    Date time = rmc.getRmcTime();
    GeoPos gp = rmc.getGp();
    double cog = rmc.getCog();
    double sog = rmc.getSog();

//    System.out.println("Date:" + date);
//    System.out.println("Time:" + time + ", [" + sdf.format(time) + "]");
//    System.out.println("Pos:" + gp.toString() + "(" + gp.lat + ", " + gp.lng + ")");
//    System.out.println("COG:" + cog);
//    System.out.println("SOG:" + sog);

    assertTrue("Invalid COG",      cog == 250);
    assertTrue("Invalid SOG",      sog == 3.9);
    assertTrue("Invalid Time",     "22:05:26".equals(sdf.format(time)));
    assertTrue("Invalid Position", (gp.lat == 37.90566666666667 && gp.lng == -122.38666666666667));
  }

  /**
   * @see StringParsers#durationToDate(String)
   */
  @Test
  public void testDurationToDate()
  {
    String str = "2006-05-05T17:35:48.000Z";
    long ld = StringParsers.durationToDate(str);
//  System.out.println(str + " => " + new Date(ld) + ", ld:" + ld);
    assertTrue("Bad duration (" + Long.toString(ld / 1000) + " instead of 1146875748)", (ld / 1000) == 1146875748L);
  }

  /**
   * @see StringParsers#validCheckSum(String)
   */
  @Test
  public void testValidCheckSum()
  {
    String str = "$IIRMC,220526.00,A,3754.34,N,12223.20,W,3.90,250,,015,E,N*07";
    boolean b = StringParsers.validCheckSum(str);
    assertTrue(str + " is invalid", b);
  }

//  public static void main(String[] args)
//  {
//    StringParsersTest spt = new StringParsersTest();
//    spt.testDurationToDate();
//    spt.testParseRMC();
//  }
}
