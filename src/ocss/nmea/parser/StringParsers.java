package ocss.nmea.parser;

import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import java.util.Map;
import java.util.TimeZone;

import user.util.GeomUtil;

public class StringParsers 
{
  /* 
   * Generic form is
   * $<talker ID><sentence ID,>[parameter 1],[parameter 2],...[<*checksum>]<CR><LF>
   * 
   * TASK Implement the following:
   * 
   * MDW Surface Wind, direction and velocity
   * VDR Set and Drift
   * VPW Device measured velocity parallel true wind
   * VWT True Wind relative bearing and velocity -> Complete
   * ZLZ Time of Day
   */

  private static Map<Integer, SVData> gsvMap = null;
  
  public static List<StringGenerator.XDRElement> parseXDR(String data)
  {
    List<StringGenerator.XDRElement> lxdr = new ArrayList<StringGenerator.XDRElement>();
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    if ((sa.length - 1) % 4 != 0) // Mismatch
    {
      System.out.println("XDR String invalid (" + sa.length + " element(s) found, expected a multiple of 4)");
      return lxdr;
    }
    for (int i=1; i<sa.length; i+=4)
    {
      String type = sa[i];
      String valStr = sa[i+1];
      String unit = sa[i+2];
      String tname = sa[i+3];
      // Valid unit and type
      boolean foundType = false;
      boolean foundUnit = false;
      for (StringGenerator.XDRTypes xdrt : StringGenerator.XDRTypes.values())
      {
        if (xdrt.type().equals(type))  
        {
          foundType = true;
          if (xdrt.unit().equals(unit))
          {
            foundUnit = true;
            try
            {
              double value = Double.parseDouble(valStr);
              lxdr.add(new StringGenerator.XDRElement(xdrt, value, tname));
            }
            catch (NumberFormatException nfe)
            {
              throw new RuntimeException(nfe);
            }
            break;
          }
        }
      }
      if (!foundType)
      {
        System.out.println("Unknown XDR type [" + type + "], in [" + data + "]");
        return lxdr;
      }
      if (!foundUnit)
      {
        System.out.println("Invalid XDR unit [" + unit + "] for type [" + type + "], in [" + data + "]");   
        return lxdr;
      }
    }
    
    return lxdr;
  }
  
  /**
   *
   * @param data
   * @return Pressure in Mb / hPa
   */
  public static double parseMMB(String data)
  {
    /*
     * Structure is $IIMMB,29.9350,I,1.0136,B*7A
     *                     |       | |      |
     *                     |       | |      Bars
     *                     |       | Pressure in Bars
     *                     |       Inches of Hg
     *                     Pressure in inches of Hg
     */
    double d = 0d;
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try 
    { 
      d = Double.parseDouble(sa[3]); 
      d *= 1000d;
    } catch (NumberFormatException nfe) {}
    return d;
  }
  
  public static double parseMTA(String data)
  {
    /*
     * Structure is $IIMTA,020.5,C*30
     *                     |     |
     *                     |     Celcius
     *                     Temperature in Celcius
     */
    double d = 0d;
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try 
    { 
      d = Double.parseDouble(sa[1]); 
    } catch (NumberFormatException nfe) {}
    return d;
  }
  
  public static Current parseVDR(String data)
  {
    /* 
     * Structure is $IIVDR,00.0,T,00.0,M,00.0,N*XX
     *                     |    | |    | |    |
     *                     |    | |    | |    Knots
     *                     |    | |    | Speed
     *                     |    | |    Mag.
     *                     |    | Magnetic Dir
     *                     |    True
     *                     True Dir
     */ 
    Current current = null;
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try
    {
      double speed = Double.parseDouble(sa[5]);
      float dir =   Float.parseFloat(sa[1]);
      current = new Current((int)Math.round(dir), speed);
    }
    catch (Exception ex) {}
    return current;
  }
    
  public static float parseBAT(String data)
  {
    /*
     * NOT STANDARD !!!
     * Structure is $XXBAT,14.82,V,1011,98*20
     *                     |     | |    |    
     *                     |     | |    Volume [0..100]    
     *                     |     | ADC [0..1023]
     *                     |     Volts
     *                     Voltage
     */
    float v = -1f;
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try 
    { 
      v = Float.parseFloat(sa[1]); 
    } catch (NumberFormatException nfe) {}
    return v;
  }
        
  public static long parseSTD(String data)
  {
    /*
     * NOT STANDARD !!!
     * Structure is $XXSTD,77672*5C
     *                     |
     *                     Cache Age in ms
     */ 
    long age = 0L;
    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try 
    { 
      age = Long.parseLong(sa[1]); 
    } catch (NumberFormatException nfe) {}    
    return age;
  }
  
  public static Map<Integer, SVData> parseGSV(String data)
  {
    String s = data.trim();
    if (s.length() < 6)
      return gsvMap;
//  System.out.println("String [" + s + "]");
    /* Structure is $GPGSV,3,1,11,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00*74
     *                     | | |  |  |  |   |  |            |            |  
     *                     | | |  |  |  |   |  |            |            Fourth SV...
     *                     | | |  |  |  |   |  |            Third SV...
     *                     | | |  |  |  |   |  Second SV...
     *                     | | |  |  |  |   SNR (0-99 db)
     *                     | | |  |  |  Azimuth in degrees (0-359)
     *                     | | |  |  Elevation in degrees (0-90)
     *                     | | |  First SV PRN Number
     *                     | | Total number of SVs in view 
     *                     | Message Number
     *                     Number of messages in this cycle
     */
    final int DATA_OFFSET = 3; // num of mess, mess num, Total num of SVs.
    final int NB_DATA     = 4; // SV num, elev, Z, SNR

    int nbMess = -1; 
    int messNum = -1;

    String sa[] = data.substring(0, data.indexOf("*")).split(",");
    try
    {
      nbMess = Integer.parseInt(sa[1]); 
      messNum = Integer.parseInt(sa[2]);
      int nbSVinView = Integer.parseInt(sa[3]);
      if (messNum == 1) // Reset
      {
        gsvMap = new HashMap<Integer, SVData>(nbSVinView);
      }

      for (int indexInSentence=1; indexInSentence<=4; indexInSentence++)
      {      
        int rnkInView = ((messNum - 1) * NB_DATA) + (indexInSentence);
        if (rnkInView <= nbSVinView)
        {          
          int svNum = 0;
          int elev  = 0;
          int z     = 0;
          int snr   = 0;
          try { svNum = Integer.parseInt(sa[DATA_OFFSET + ((indexInSentence - 1) * NB_DATA) + 1]); } catch (Exception pex) {}
          try { elev  = Integer.parseInt(sa[DATA_OFFSET + ((indexInSentence - 1) * NB_DATA) + 2]); } catch (Exception pex) {}
          try { z     = Integer.parseInt(sa[DATA_OFFSET + ((indexInSentence - 1) * NB_DATA) + 3]); } catch (Exception pex) {}
          try { snr   = Integer.parseInt(sa[DATA_OFFSET + ((indexInSentence - 1) * NB_DATA) + 4]); } catch (Exception pex) {}
          SVData svd = new SVData(svNum, elev, z, snr);
          if (gsvMap != null) gsvMap.put(svNum, svd);          
//        System.out.println("SV #" + rnkInView + ", SV:" + svNum + " H:"+ elev + ", Z:" + z + ", snr:" + snr);
        }
      }      
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
    if (messNum != -1 && nbMess != -1 && messNum == nbMess)
      return gsvMap;
    
    return null;
  }
  
  public static String GSVtoString( Map<Integer, SVData> hm)
  {
    String str = "";
    if (hm != null)
    {
      str += (hm.size() + " Satellites in view:");
      for (Integer sn : hm.keySet())
      {
        SVData svd = hm.get(sn);
        str += ("Satellite #" + svd.getSvID() + " Elev:" + svd.getElevation() + ", Z:" + svd.getAzimuth() + ", SNR:" + svd.getSnr() + "db. ");
      }
    }
    return str.trim(); 
  }
  
  public static List<Object> parseGGA(String data)
  {
    final int KEY_POS = 0;
    final int UTC_POS = 1;
    final int LAT_POS = 2;
    final int LAT_SGN_POS = 3;
    final int LONG_POS = 4;
    final int LONG_SGN_POS = 5;
    final int GPS_Q_POS = 6;
    final int NBSAT_POS = 7;
    final int ANTENNA_ALT = 9;
    
    ArrayList<Object> al = null;
    String s = data.trim();
    if (s.length() < 6)
      return al;
    /* Structure is 
     *  $GPGGA,014457,3739.853,N,12222.821,W,1,03,5.4,1.1,M,-28.2,M,,*7E         
     *  $aaGGA,hhmmss.ss,llll.ll,a,gggg.gg,a,x,xx,x.x,x.x,M,x.x,M,x.x,xxxx*hh(CR)(LF)
     *         |         |         |         | |  |   |   | |   | |   |
     *         |         |         |         | |  |   |   | |   | |   Differential reference station ID
     *         |         |         |         | |  |   |   | |   | Age of differential GPS data (seconds) 
     *         |         |         |         | |  |   |   | |   Unit of geodial separation, meters
     *         |         |         |         | |  |   |   | Geodial separation
     *         |         |         |         | |  |   |   Unit of antenna altitude, meters
     *         |         |         |         | |  |   Antenna altitude above sea level
     *         |         |         |         | |  Horizontal dilution of precision
     *         |         |         |         | number of satellites in use 00-12 (in use, not in view!)
     *         |         |         |         GPS quality indicator (0:invalid, 1:GPS fix, 2:DGPS fix)
     *         |         |         Longitude
     *         |         Latitude
     *         UTC of position
     */    
    String sa[] = s.substring(0, s.indexOf("*")).split(",");
    double utc = 0L, lat = 0L, lng = 0L;
    int nbsat = 0;
    try 
    { utc = parseNMEADouble(sa[UTC_POS]); } 
    catch (Exception ex) {}
    
    try 
    { 
      double l = parseNMEADouble(sa[LAT_POS]);
      int intL = (int)l/100;
      double m = ((l/100.0)-intL) * 100.0;
      m *= (100.0/60.0);
      lat = intL + (m/100.0);
      if ("S".equals(sa[LAT_SGN_POS]))
        lat = -lat;
    } 
    catch (Exception ex) {}
    try 
    { 
      double g = parseNMEADouble(sa[LONG_POS]);
      int intG = (int)g/100;
      double m = ((g/100.0)-intG) * 100.0;
      m *= (100.0/60.0);
      lng = intG + (m/100.0);
      if ("W".equals(sa[LONG_SGN_POS]))
        lng = -lng;
    } 
    catch (Exception ex) {}
    try { nbsat = Integer.parseInt(sa[NBSAT_POS]); } catch (Exception ex) {}
    
//  System.out.println("UTC:" + utc + ", lat:" + lat + ", lng:" + lng + ", nbsat:" + nbsat); 
    int h = (int)(utc / 10000);
    int m = (int)((utc - (h * 10000)) / 100);
    float sec = (float)(utc - ((h * 10000) + (m * 100)));
//  System.out.println(h + ":" + m + ":" + sec);
    
//  System.out.println(new GeoPos(lat, lng).toString());
//  System.out.println("Done.");
    
    double alt = 0;
    try 
    { alt = parseNMEADouble(sa[ANTENNA_ALT]); } 
    catch (Exception ex) {}
    
    al = new ArrayList<Object>(4);
    al.add(new UTC(h, m, sec));
    al.add(new GeoPos(lat, lng));
    al.add(nbsat);
    al.add(alt);
    
    return al;
  }  
  
  public static GSA parseGSA(String data)
  {
    /*
     * $GPGSA,A,3,19,28,14,18,27,22,31,39,,,,,1.7,1.0,1.3*35
     *        | | |                           |   |   |
     *        | | |                           |   |   VDOP
     *        | | |                           |   HDOP
     *        | | |                           PDOP (dilution of precision). No unit; the smaller the better.
     *        | | IDs of the SVs used in fix (up to 12)
     *        | Mode: 1=Fix not available, 2=2D, 3=3D
     *        Mode: M=Manual, forced to operate in 2D or 3D
     *              A=Automatic, 3D/2D
     */
    GSA gsa = new GSA();
    String[] elements = data.substring(0, data.indexOf("*")).split(",");
    if (elements.length >= 2)
    {
      if ("M".equals(elements[1]))
        gsa.setMode1(GSA.ModeOne.Manual);
      if ("A".equals(elements[1]))
        gsa.setMode1(GSA.ModeOne.Auto);
    }
    if (elements.length >= 3)
    {
      if ("1".equals(elements[2]))
        gsa.setMode2(GSA.ModeTwo.NoFix);
      if ("2".equals(elements[2]))
        gsa.setMode2(GSA.ModeTwo.TwoD);
      if ("3".equals(elements[2]))
        gsa.setMode2(GSA.ModeTwo.ThreeD);
    }
    for (int i=3; i<15; i++)
    {
      if (elements[i].trim().length() > 0)
      {
        int sv = Integer.parseInt(elements[i]);
        gsa.getSvArray().add(sv);
      }
    }
    if (elements.length >= 16)
      gsa.setPDOP(Float.parseFloat(elements[15]));
    if (elements.length >= 17)
      gsa.setHDOP(Float.parseFloat(elements[16]));
    if (elements.length >= 18)
      gsa.setVDOP(Float.parseFloat(elements[17]));
    
    return gsa;
  }  
  
  public final static int BSP_in_VHW = 0;
  public final static int HDM_in_VHW = 1;
  public final static int HDG_in_VHW = 2;
  
  public static double[] parseVHW(String data)
  {
    return parseVHW(data, 0d);
  }
  public static double[] parseVHW(String data, double defaultBSP)
  {
    String s = data.trim();
    if (s.length() < 6)
      return (double[])null;
    /* Structure is 
     *         1   2 3   4 5   6 7   8
     *  $aaVHW,x.x,T,x.x,M,x.x,N,x.x,K*hh(CR)(LF)
     *         |     |     |     |
     *         |     |     |     Speed in km/h
     *         |     |     Speed in knots
     *         |     Heading in degrees, Magnetic
     *         Heading in degrees, True   
     */
    // We're interested only in Speed in knots.
    double speed = defaultBSP;
    double hdm   = 0d;
    double hdg   = 0d;

    try
    {
      String[] nmeaElements = data.substring(0, data.indexOf("*")).split(",");
      try { speed = parseNMEADouble(nmeaElements[5]); } catch (Exception ex) {}
      try { hdm   = parseNMEADouble(nmeaElements[3]); } catch (Exception ex) {}
      try { hdg   = parseNMEADouble(nmeaElements[1]); } catch (Exception ex) {}
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      return (double[])null;
    }    

    return new double[] { speed, hdm, hdg };
  }
  
  public static String parseVHWtoString(String s)
  {
    String ret = "";
    try { ret = Double.toString(parseVHW(s)[BSP_in_VHW]); } catch (Exception ignore) {}
    return ret; 
  }

  public final static int LOG_in_VLW      = 0;
  public final static int DAILYLOG_in_VLW = 1;
  
  public static double[] parseVLW(String data)
  {
    String s = data.trim();
    if (s.length() < 6)
      return (double[])null;
    
    double cumulative = 0d;
    double sinceReset = 0d;
    /* Structure is
     * $aaVLW,x.x,N,x.x,N*hh<CR><LF> 
     *        |   | |   |
     *        |   | |   Nautical miles
     *        |   | Distance since reset
     *        |   Nautical miles
     *        Total cumulative distance
     */
    try
    {
      String[] nmeaElements = data.substring(0, data.indexOf("*")).split(",");
      cumulative = parseNMEADouble(nmeaElements[1]);
      sinceReset = parseNMEADouble(nmeaElements[3]);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      return (double[])null;
    }
    return new double[] { cumulative, sinceReset };
  }

  public static double parseMTW(String data)
  {
    /* Structure
     * $xxMTW,+18.0,C*hh
     * 
     */
    String s = data.trim();
    if (s.length() < 6)
      return 0d;
    
    double temp = 0d;
    try
    {
      String[] nmeaElements = data.substring(0, data.indexOf("*")).split(",");
      String _s = nmeaElements[1];
      if (_s.startsWith("+")) _s = _s.substring(1);
      temp = parseNMEADouble(_s);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      return 0d;
    }
    return temp;
  }
    
  public static final int TRUE_WIND     = 0;
  public static final int APPARENT_WIND = 1;
  // AWA, AWS (R), possibly TWA, TWS (T)
  public static Wind parseMWV(String data)
  {
    int flavor = -1;
    
    String s = data.trim();
    if (s.length() < 6)
      return null;
    /* Structure is 
     *  $aaMWV,x.x,a,x.x,a,A*hh
     *         |   | |   | |
     *         |   | |   | status : A=data valid
     *         |   | |   Wind Speed unit (K/M/N)
     *         |   | Wind Speed
     *         |   reference R=relative, T=true
     *         Wind angle 0 to 360 degrees 
     */
    // We're interested only in Speed in knots.
    Wind aw = null;
    try
    {
      if (s.indexOf("A*") == -1) // Data invalid
        return aw;
      else
      {
        String speed = "", angle = "";
        if (s.indexOf("MWV,") > -1 && s.indexOf(",R,") > -1) // Apparent
        {
          flavor = APPARENT_WIND;
          angle = s.substring(s.indexOf("MWV,") + "MWV,".length(), s.indexOf(",R,"));        
        }
        if (s.indexOf(",R,") > -1 && s.indexOf(",N,") > -1)
        {
          speed = s.substring(s.indexOf(",R,") + ",R,".length(), s.indexOf(",N,"));         
        }
        if (speed.trim().length() == 0 && angle.trim().length() == 0)
        {
          if (s.indexOf("MWV,") > -1 && s.indexOf(",T,") > -1)
          {
            flavor = TRUE_WIND;
            angle = s.substring(s.indexOf("MWV,") + "MWV,".length(), s.indexOf(",T,"));    // True    
          }
          if (s.indexOf(",T,") > -1 && s.indexOf(",N,") > -1)
          {
            speed = s.substring(s.indexOf(",T,") + ",T,".length(), s.indexOf(",N,"));        
          }
        }
        float awa = 0f;
        double aws = 0d;
        try { awa = parseNMEAFloat(angle); } catch (Exception ex) {}
        try { aws = parseNMEADouble(speed); } catch (Exception ex) {}
        if (flavor == APPARENT_WIND)
          aw = new ApparentWind(Math.round(awa), aws);
        else if (flavor == TRUE_WIND)
          aw = new TrueWind(Math.round(awa), aws);
        else
          System.out.println("UNKNOWN wind type!");
      }
    }
    catch (Exception e)
    {
      System.err.println("parseMWV for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return aw;
  }
  
  /*
   * $--VWT,x.x,a,x.x,N,x.x,M,x.x,K*hh<CR><LF>
   *        |     |     |     |  
   *        |     |     |     Wind speed, Km/Hr
   *        |     |     Wind speed, meters/second
   *        |     Calculated wind Speed, knots
   *        Calculated wind angle relative to the vessel, 0 to 180, left/right L/R of vessel heading
   */
  public static Wind parseVWT(String data)
  {
    Wind wind = null;
    String s = data.trim();
    if (s.length() < 6)
      return null;
    try
    {
      // TODO Implement?
    }
    catch (Exception e)
    {
      System.err.println("parseVWT for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return wind;
  }
  
  public static String parseMWVtoString(String s)
  { 
    String ret = "";
    try { ret = parseMWV(s).toString(); } catch (Exception ignore){}
    return ret; 
  }
  public static String getAWAFromMWV(String s)
  {
    String awa = "";
    try
    {
      Wind wind = parseMWV(s);
      awa = Integer.toString(wind.angle);
    }
    catch (Exception ex)
    { awa = "-"; }
    return awa;
  }
  public static String getAWSFromMWV(String s)
  {
    String aws = "";
    try
    {
      Wind wind = parseMWV(s);
      aws = Double.toString(wind.speed);
    }
    catch (Exception ex)
    { aws = "-"; }                    
    return aws;
  }
  
  // AWA, AWS
  // Example: VWR,148.,L,02.4,N,01.2,M,04.4,K*XX
  public static Wind parseVWR(String data)
  {
    String s = data.trim();
    if (s.length() < 6)
      return null;
    /* Structure is 
     *  $aaVWR,x.x,a,x.x,N,x.x,M,x.x,K*hh
     *         |   | |     |     |
     *         |   | |     |     Wind Speed, in km/h
     *         |   | |     Wind Speed, in m/s
     *         |   | Wind Speed, in knots
     *         |   L=port, R=starboard
     *         Wind angle 0 to 180 degrees 
     */
    // We're interested only in Speed in knots.
    Wind aw = null;
    try
    {
      if (false && s.indexOf("K*") == -1) // Data invalid // Watafok???
        return aw;
      else
      {
        String speed = "", angle = "", side = "";
        int firstCommaIndex = s.indexOf(",");
        int secondCommaIndex = s.indexOf(",", firstCommaIndex + 1);
        int thirdCommaIndex = s.indexOf(",", secondCommaIndex + 1);
        int fourthCommaIndex = s.indexOf(",", thirdCommaIndex + 1);
        if (firstCommaIndex > -1 && secondCommaIndex > -1)
          angle = s.substring(firstCommaIndex + 1, secondCommaIndex);    
        while (angle.endsWith("."))
          angle = angle.substring(0, angle.length() - 1);
        if (secondCommaIndex > -1 && thirdCommaIndex > -1)
          side = s.substring(secondCommaIndex + 1, thirdCommaIndex);        
        if (thirdCommaIndex > -1 && fourthCommaIndex > -1)
          speed = s.substring(thirdCommaIndex + 1, fourthCommaIndex);     
        double ws = 0d;
        try { ws = parseNMEADouble(speed); } catch (Exception ex) {}
        int wa = 0;
        try { wa = Integer.parseInt(angle); } catch (Exception ex) {}
        if (side.equals("L"))
          wa = 360 - wa;
        aw = new ApparentWind(wa, ws);
      }
    }
    catch (Exception e)
    {
      System.err.println("parseMWV for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return aw;
  }
  public static String parseVWRtoString(String s)
  {
    String ret = "";
    try { ret = parseVWR(s).toString(); } catch (Exception ignore) {}
    return ret; 
  }
  public static String getAWAFromVWR(String s)
  {
    String awa = "";
    try 
    {
      Wind wind = parseVWR(s);
      awa = Integer.toString(wind.angle);
    }
    catch (Exception ex)
    { awa = "-"; }
    return awa;
  }
  public static String getAWSFromVWR(String s)
  {
    String aws = "";
    try
    {
      Wind wind = parseVWR(s);
      aws = Double.toString(wind.speed);
    }
    catch (Exception ex)
    { aws = "-"; }
    return aws;
  }
 
  public static OverGround parseVTG(String data)
  {
    String s = data.trim();
    OverGround og = null;
    if (s.length() < 6)
      return null;
    /* Structure is 
     * $IIVTG,x.x,T,x.x,M,x.x,N,x.x,K,A*hh
              |   | |  |  |   | |___|SOG, km/h
              |   | |  |  |___|SOG, knots
              |   | |__|COG, mag
              |___|COG, true

       $IIVTG,17.,T,M,7.9,N,,*36 // B&G does this...
       $IIVTG,,T,338.,M,N,,*28   // or this...
       $IIVTG,054.7,T,034.4,M,005.5,N,010.2,K,A*XX
              054.7,T      True track made good
              034.4,M      Magnetic track made good
              005.5,N      Ground speed, knots
              010.2,K      Ground speed, Kilometers per hour
     */
    // We're interested only in Speed in knots.
    try
    {
      if (false && s.indexOf("A*") == -1) // Data invalid, only for NMEA 2.3 and later
        return og;
      else
      {
        String speed = "", angle = "";
        String[] sa = s.split(",");
        
        int tIndex = -1;
        for (int i=0; i<sa.length; i++)
        {
          if ("T".equals(sa[i]))
          {
            tIndex = i;
            break;
          }
        }
        int nIndex = -1;
        for (int i=0; i<sa.length; i++)
        {
          if ("N".equals(sa[i]))
          {
            nIndex = i;
            break;
          }
        }        
        angle = sa[tIndex - 1];
        speed = sa[nIndex - 1];
        if (speed.endsWith("."))
          speed += "0";
        double sog = parseNMEADouble(speed);
        if (angle.endsWith("."))
          angle += "0";
        int cog = (int)Math.round(parseNMEADouble(angle));
        og = new OverGround(sog, cog);
      }
    }
    catch (Exception e)
    {
      System.err.println("parseVTG for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return og;
  }
  public static String parseVTGtoString(String s)
  { 
    String ret = "";
    try { ret = parseVTG(s).toString(); } catch (Exception ignore) {}
    return ret; 
  }
  public static String getCOGFromVTG(String s)
  {
    String cog = "";
    try
    {
      OverGround og = parseVTG(s);
      cog = Integer.toString(og.getCourse());
    }
    catch (Exception ex)
    { cog = "-"; }
    return cog;
  }
  public static String getSOGFromVTG(String s)
  {
    String sog = "";
    try
    {
      OverGround og = parseVTG(s);
      sog = Double.toString(og.getSpeed());
    }
    catch (Exception ex)
    { sog = "-"; }
    return sog;
  }
  
  public final static int GP_in_GLL   = 0;
  public final static int DATE_in_GLL = 1;
  // Geographical Latitude & Longitude
  public static Object[] parseGLL(String data)
  {
    String s = data.trim();
    if (s.length() < 6)
      return (Object[])null;
    /* Structure is 
     *  $aaGLL,llll.ll,a,gggg.gg,a,hhmmss.ss,A*hh
     *         |       | |       | |         |
     *         |       | |       | |         A:data valid
     *         |       | |       | UTC of position
     *         |       | |       Long sign :E/W
     *         |       | Longitude
     *         |       Lat sign :N/S
     *         Latitude
     */
    GeoPos ll = null;
    Date date = null;
    try
    {
      if (s.indexOf("A*") == -1) // Data invalid
        return (Object[])null;
      else
      {
        int i = s.indexOf(",");
        if (i > -1)
        {
          String lat = "";
          int j = s.indexOf(",", i+1);
          lat = s.substring(i+1, j);
          double l = parseNMEADouble(lat);
          int intL = (int)l/100;
          double m = ((l/100.0)-intL) * 100.0;
          m *= (100.0/60.0);
          l = intL + (m/100.0);
          String latSgn = s.substring(j+1, j+2);
          if (latSgn.equals("S"))
            l *= -1.0;
          int k = s.indexOf(",", j+3);
          String lng = s.substring(j+3, k);
          double g = parseNMEADouble(lng);
          int intG = (int)g/100;
          m = ((g/100.0)-intG) * 100.0;
          m *= (100.0/60.0);
          g = intG + (m/100.0);
          String lngSgn = s.substring(k+1, k+2);
          if (lngSgn.equals("W"))
            g *= -1.0;
          
          ll = new GeoPos(l, g);       
          k = s.indexOf(",", k+2);
          String dateStr = s.substring(k + 1);
          if (dateStr.indexOf(",") > 0)
            dateStr = dateStr.substring(0, dateStr.indexOf(","));
          double utc = 0D;
          try { utc = parseNMEADouble(dateStr); } catch (Exception ex) { /*System.out.println("dateStr in StringParsers.parseGLL"); */ }
          int h = (int)(utc / 10000);
          int mn = (int)((utc - (10000 * h)) / 100);
          float sec = (float)(utc % 100f);
          Calendar local = new GregorianCalendar();
          local.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
          local.set(Calendar.YEAR, 1970);
          local.set(Calendar.MONDAY, Calendar.JANUARY);
          local.set(Calendar.DAY_OF_MONTH, 1);
          local.set(Calendar.HOUR_OF_DAY, h);  
          local.set(Calendar.MINUTE, mn);
          local.set(Calendar.SECOND, (int)Math.round(sec));
          local.set(Calendar.MILLISECOND, 0);
          try { date = local.getTime(); } catch (Exception ex) {}
        }
      }
//    System.out.println(str);
    }
    catch (Exception e)
    {
      System.err.println("parseGLL for [" + s + "] " + e.toString());
//    e.printStackTrace();
    }
    return new Object[] { ll, date };
  }
  public static String parseGLLtoString(String s)
  { 
    String ret = "";
    try { ret = parseGLL(s).toString(); } catch (Exception ignore) {}
    return ret; 
  }
  public static String getLatFromGLL(String s)
  {
    String result = "";
    try
    {
      GeoPos pos = (GeoPos)parseGLL(s)[GP_in_GLL];
      result = Double.toString(pos.lat);
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  public static String getLongFromGLL(String s)
  {
    String result = "";
    try
    {
      GeoPos pos = (GeoPos)parseGLL(s)[GP_in_GLL];
      result = Double.toString(pos.lng);
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  
  public static int parseHDT(String data)
  {
    final int KEY_POS = 0;
    final int HDG_POS = 1;
    final int MT_POS  = 2;
    String s = data.trim();
    if (s.length() < 6)
      return -1;
    /* Structure is 
     *  $aaHDT,xxx,M*hh(CR)(LF)
     *         |   |   
     *         |   Magnetic, True
     *         Heading in degrees
     */
    int hdg = 0;
    
    String[] elmts = data.substring(0, data.indexOf("*")).split(",");
    try
    {
      if (elmts[KEY_POS].indexOf("HDT") > -1)
      {
        if ("T".equals(elmts[MT_POS]))
          hdg = Math.round(parseNMEAFloat(elmts[HDG_POS]));
        else
          throw new RuntimeException("Wrong type [" + elmts[HDG_POS] + "] in parseHDT.");
      }
      else
        System.err.println("Wrong chain in parseHDT [" + data + "]");
    }
    catch (Exception e)
    {
      System.err.println("parseHDT for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return hdg;
  }
  // Heading (Mag.)
  public static int parseHDM(String data)
  {
    final int KEY_POS = 0;
    final int HDG_POS = 1;
    final int MT_POS  = 2;
    String s = data.trim();
    if (s.length() < 6)
      return -1;
    /* Structure is 
     *  $aaHDG,xxx,M*hh(CR)(LF)
     *         |   |   
     *         |   Magnetic, True
     *         Heading in degrees
     */
    int hdg = 0;
    
    String[] elmts = data.substring(0, data.indexOf("*")).split(",");
    try
    {
      if (elmts[KEY_POS].indexOf("HDM") > -1)
      {
        if ("M".equals(elmts[MT_POS]))
          hdg = Math.round(parseNMEAFloat(elmts[HDG_POS]));
        else
          throw new RuntimeException("Wrong type [" + elmts[HDG_POS] + "] in parseHDM.");
      }
      else
        System.err.println("Wrong chain in parseHDM [" + data + "]");
    }
    catch (Exception e)
    {
      System.err.println("parseHDM for " + s + ", " + e.toString());
//    e.printStackTrace();
    }
    return hdg;
  }
  public static String parseHDMtoString(String s)
  { 
    String ret = "";
    try { ret = Integer.toString(parseHDM(s)); } catch (Exception ignore) {}
    return ret; 
  }

  public final static int HDG_in_HDG = 0;
  public final static int DEV_in_HDG = 1;
  public final static int VAR_in_HDG = 2;
  
  public static double[] parseHDG(String data)
  {
    double[] ret = null;
    String s = data.trim();
    if (s.length() < 6)
      return ret;
        
    double hdg = 0d;
    double dev = -Double.MAX_VALUE;
    double var = -Double.MAX_VALUE;
    /* Structure is
     * $xxHDG,x.x,x.x,a,x.x,a*hh<CR><LF>
     *        |   |   | |   | | 
     *        |   |   | |   | Checksum
     *        |   |   | |   Magnetic Variation direction, E = Easterly, W = Westerly
     *        |   |   | Magnetic Variation degrees
     *        |   |   Magnetic Deviation direction, E = Easterly, W = Westerly
     *        |   Magnetic Deviation, degrees
     *        Magnetic Sensor heading in degrees
     */
    try
    {
      String[] nmeaElements = data.substring(0, data.indexOf("*")).split(",");
      try { hdg = parseNMEADouble(nmeaElements[1]); } catch (Exception ex) {}
      try { dev = parseNMEADouble(nmeaElements[2]); } catch (Exception ex) {}
      if (nmeaElements.length > 3 && nmeaElements[3] != null && "W".equals(nmeaElements[3]))
        dev = -dev;
      try { var = parseNMEADouble(nmeaElements[4]); } catch (Exception ex) {}
      if (nmeaElements.length > 5 && nmeaElements[5] != null && "W".equals(nmeaElements[5]))
        var = -var;
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      return (double[])null;
    }
    ret = new double[] { hdg, dev, var };
    
    return ret;
  }
  
  public static String parseHDGtoString(String s)
  { 
    String ret = "";
    try { ret = Integer.toString((int)parseHDG(s)[HDG_in_HDG]); } catch (Exception ignore) {}
    return ret; 
  }

  // Recommended Minimum Navigation Information
  public static RMB parseRMB(String str)
  {
    /*        1 2   3 4    5    6       7 8        9 10  11  12  13 
     * $GPRMB,A,x.x,a,c--c,d--d,llll.ll,e,yyyyy.yy,f,g.g,h.h,i.i,j*kk
     *        | |   | |    |    |       | |        | |   |   |   |
     *        | |   | |    |    |       | |        | |   |   |   A=Entered or perpendicular passed, V:not there yet
     *        | |   | |    |    |       | |        | |   |   Destination closing velocity in knots
     *        | |   | |    |    |       | |        | |   Bearing to destination, degrees, True
     *        | |   | |    |    |       | |        | Range to destination, nm
     *        | |   | |    |    |       | |        E or W
     *        | |   | |    |    |       | Destination Waypoint longitude
     *        | |   | |    |    |       N or S
     *        | |   | |    |    Destination Waypoint latitude
     *        | |   | |    Destination Waypoint ID
     *        | |   | Origin Waypoint ID 
     *        | |   Direction to steer (L or R) to correct error
     *        | Crosstrack error in nm
     *        Data Status (Active or Void)
     */
    RMB rmb = null;
    String s = str.trim();
    if (s.length() < 6)
      return null;
    
    try
    {
      if (s.indexOf("RMB,") > -1)
      {
        rmb = new RMB();
        String[] data = str.substring(0, str.indexOf("*")).split(",");
        if (data[1].equals("V")) // Void
          return null;
        double xte = 0d;
        try { xte = parseNMEADouble(data[2]); } catch (Exception ex) {}
        rmb.setXte(xte);
        rmb.setDts(data[3]);
        rmb.setOwpid(data[4]);        
        rmb.setDwpid(data[5]);
        
        double _lat = 0d;
        try { _lat = parseNMEADouble(data[6]); } catch (Exception ex) {}
        double lat = (int)(_lat / 100d) + ((_lat % 100d) / 60d);
        if ("S".equals(data[7])) lat = -lat;
        double _lng = 0d;
        try { _lng = parseNMEADouble(data[8]); } catch (Exception ex) {}
        double lng = (int)(_lng / 100d) + ((_lng % 100d) / 60d);
        if ("W".equals(data[9])) lng = -lng;
        rmb.setDest(new GeoPos(lat, lng));
        double rtd = 0d;
        try { rtd = parseNMEADouble(data[10]); } catch (Exception ex) {}
        rmb.setRtd(rtd);
        double btd = 0d;
        try { btd = parseNMEADouble(data[11]); } catch (Exception ex) {}
        rmb.setBtd(btd);
        double dcv = 0d;
        try { dcv = parseNMEADouble(data[12]); } catch (Exception ex) {}
        rmb.setDcv(dcv);
        rmb.setAs(data[13]);
      }
    }
    catch (Exception e)
    {
      System.err.println("parseRMB for " + s + ", " + e.toString());
    }
    return rmb;
  }
  
  // Recommended minimum specific GPS/Transit data
  public static RMC parseRMC(String str)
  {
    RMC rmc = null;
    String s = str.trim();
    if (s.length() < 6 || s.indexOf("*") < 0)
      return null;
    if (!validCheckSum(str))
      return null;
    s = s.substring(0, s.indexOf("*"));
    /* Structure is 
     *         1      2 3        4 5         6 7     8     9      10    11
     *  $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
     *         |      | |        | |         | |     |     |      |     |
     *         |      | |        | |         | |     |     |      |     Variation sign
     *         |      | |        | |         | |     |     |      Variation value
     *         |      | |        | |         | |     |     Date DDMMYY
     *         |      | |        | |         | |     COG
     *         |      | |        | |         | SOG
     *         |      | |        | |         Longitude Sign
     *         |      | |        | Longitude Value
     *         |      | |        Latitude Sign
     *         |      | Latitude value
     *         |      Active or Void
     *         UTC
     */
    try
    {
      if (s.indexOf("RMC,") > -1)
      {
        rmc = new RMC();
        String[] data = str.substring(0, str.indexOf("*")).split(",");
        if (data[2].equals("V")) // Void
          return rmc;
        if (data[1].length() > 0) // Time and Date
        {
          double utc = 0D;
          try { utc = parseNMEADouble(data[1]); } catch (Exception ex) { System.out.println("data[1] in StringParsers.parseRMC"); }
          int h = (int)(utc / 10000);
          int m = (int)((utc - (10000 * h)) / 100);
          float sec = (float)(utc % 100f);
          
//        System.out.println("Data[1]:" + data[1] + ", h:" + h + ", m:" + m + ", s:" + sec);
          
          Calendar local = new GregorianCalendar();
          local.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
          local.set(Calendar.HOUR_OF_DAY, h);  
          local.set(Calendar.MINUTE, m);
          local.set(Calendar.SECOND, (int)Math.round(sec));
          local.set(Calendar.MILLISECOND, 0);
          if (data[9].length() > 0)
          {
            int d = 1;
            try { d = Integer.parseInt(data[9].substring(0, 2)); } catch (Exception ex) {}
            int mo = 0;
            try { mo = Integer.parseInt(data[9].substring(2, 4)) - 1; } catch (Exception ex) {}
            int y = 0;
            try { y = Integer.parseInt(data[9].substring(4)); } catch (Exception ex) {}
            if (y > 50)
              y += 1900;
            else
              y += 2000;
            local.set(Calendar.DATE, d);
            local.set(Calendar.MONTH, mo);
            local.set(Calendar.YEAR, y);
            
            Date rmcDate = local.getTime();
            rmc.setRmcDate(rmcDate);
          }
          Date rmcTime = local.getTime();
          rmc.setRmcTime(rmcTime);
//        System.out.println("GPS date:" + rmcDate.toString());
        }
        if (data[3].length() > 0 && data[5].length() > 0)
        {
          String deg = data[3].substring(0, 2);
          String min = data[3].substring(2);
          double l = GeomUtil.sexToDec(deg, min);
          if ("S".equals(data[4]))
            l = -l;
          deg = data[5].substring(0, 3);
          min = data[5].substring(3);
          double g = GeomUtil.sexToDec(deg, min);
          if ("W".equals(data[6]))
            g = -g;
          rmc.setGp(new GeoPos(l, g));
        }
        if (data[7].length() > 0)
        {
          double speed = 0;
          try { speed = parseNMEADouble(data[7]); } catch (Exception ex) {}
          rmc.setSog(speed);
        }
        if (data[8].length() > 0)
        {
          double cog = 0;
          try { cog = parseNMEADouble(data[8]); } catch (Exception ex) {}
          rmc.setCog(cog);
        }
        if (data[10].length() > 0 && data[11].length() > 0)
        {
          double d = -Double.MAX_VALUE;
          try { d = parseNMEADouble(data[10]);  } catch (Exception ex) {}
          if ("W".equals(data[11]))
            d = -d;
          rmc.setDeclination(d);
        }
      }
    }
    catch (Exception e)
    {
      System.err.println("parseRMC for " + s + ", " + e.toString());
   // e.printStackTrace();
    }
    return rmc;
  }
  
  public static String parseRMCtoString(String data)
  { 
    String ret = "";
    try { ret = parseRMC(data).toString(); } catch (Exception ignore){} 
    return ret; 
  }
  
  public static String getLatFromRMC(String s)
  {
    String result = "";
    try
    {
      RMC rmc = parseRMC(s);
      result = Double.toString(rmc.getGp().lat);
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  public static String getLongFromRMC(String s)
  {
    String result = "";
    try
    {
      RMC rmc = parseRMC(s);
      result = Double.toString(rmc.getGp().lng);
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  public static String getCOGFromRMC(String s)
  {
    String result = "";
    try
    {
      RMC rmc = parseRMC(s);
      result = Double.toString(rmc.getCog());
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  public static String getSOGFromRMC(String s)
  {
    String result = "";
    try
    {
      RMC rmc = parseRMC(s);
      result = Double.toString(rmc.getSog());
    }
    catch (Exception ex)
    { result = "-"; }
    return result;
  }
  
  public final static int MESS_NUM = 0;
  public final static int NB_MESS  = 1;
  /**
   * For GSV, returns the message number, and the total number of messages to expect.
   * 
   * @param gsvString
   * @return
   */
  public static int[] getMessNum(String gsvString)
  {
    int mn  = -1;
    int nbm = -1;
    if (validCheckSum(gsvString))
    {
      String[] elmt = gsvString.trim().split(",");
      try
      {
        nbm = Integer.parseInt(elmt[1]);
        mn  = Integer.parseInt(elmt[2]);
      }
      catch (Exception ex)
      {
        ex.printStackTrace();
      }
    }
    return new int[] { mn, nbm };
  }
  
  public static UTC parseZDA(String str)
  {
    /* Structure is 
     * $GPZDA,hhmmss.ss,dd,mm,yyyy,xx,yy*CC
     * $GPZDA,201530.00,04,07,2002,00,00*60    
     *        |         |  |  |    |  |
     *        |         |  |  |    |  local zone minutes 0..59
     *        |         |  |  |    local zone hours -13..13
     *        |         |  |  year
     *        |         |  month
     *        |         day
     *        HrMinSec(UTC)
     */
    String[] data = str.substring(0, str.indexOf("*")).split(",");
    UTC utc = new UTC(Integer.parseInt(data[1].substring(0, 2)),
                      Integer.parseInt(data[1].substring(2, 4)),
                      Float.parseFloat(data[1].substring(4)));
    
    return utc;
  }
  
  public static final short DEPTH_IN_FEET    = 0;
  public static final short DEPTH_IN_METERS  = 1;
  public static final short DEPTH_IN_FATHOMS = 2;
  
  public static String parseDBTinMetersToString(String data)
  {
    String s = data.trim();
    String sr = "";
    try 
    { 
      float f = parseDBT(s, DEPTH_IN_METERS);
      sr = Float.toString(f); 
    } 
    catch (Exception ex) 
    {
      sr = "-";
    }
    return sr;
  }
  
  private final static double METERS_TO_FEET    = 3.28083;
  // Depth 
  public static float parseDPT(String data, short unit)
  {
    String s = data.trim();
    if (s.length() < 6)
      return -1F;
    /* Structure is 
     *  $xxDPT,XX.XX,XX.XX,XX.XX*hh<0D><0A>
     *         |     |     |    
     *         |     |     Max depth in meters
     *         |     offset
     *         Depth in meters
     */
    float feet    = 0.0F;
    float meters  = 0.0F;
    float fathoms = 0.0F;
    String[] array = data.substring(0, data.indexOf("*")).split(",");
    try
    {
      meters = parseNMEAFloat(array[1]);
      try 
      {
        String strOffset = array[2].trim();
        if (strOffset.startsWith("+"))
          strOffset = strOffset.substring(1);
        float offset = parseNMEAFloat(strOffset);
        meters += offset;
      } 
      catch (Exception ex) {}
      feet   = meters * (float)METERS_TO_FEET;
      fathoms = feet / 6F;
    }
    catch (Exception e)
    {
      System.err.println("parseDPT For " + s + ", " + e.toString());
  //  e.printStackTrace();
    }

    if (unit == DEPTH_IN_FEET)
      return feet;
    else if (unit == DEPTH_IN_METERS)
      return meters;
    else if (unit == DEPTH_IN_FATHOMS)
      return fathoms;
    else
      return meters;
  }
  
  // Depth Below Transducer
  public static float parseDBT(String data, short unit)
  {
    String s = data.trim();
    if (s.length() < 6)
      return -1F;
    /* Structure is 
     *  $aaDBT,011.0,f,03.3,M,01.8,F*18(CR)(LF)
     *         |     | |    | |    |
     *         |     | |    | |    F for fathoms
     *         |     | |    | Depth in fathoms
     *         |     | |    M for meters
     *         |     | Depth in meters
     *         |     f for feet
     *         Depth in feet   
     */
    float feet    = 0.0F;
    float meters  = 0.0F;
    float fathoms = 0.0F;
    String str = "";
    String first = "", last = "";
    try
    {
      first = "DBT,";
      last  = ",f,";
      if (s.indexOf(first) > -1 && s.indexOf(last) > -1)
      {
        if (s.indexOf(first) < s.indexOf(last))
          str = s.substring(s.indexOf(first) + first.length(), s.indexOf(last));        
      }
      feet = parseNMEAFloat(str);
      first = ",f,";
      last  = ",M,";
      if (s.indexOf(first) > -1 && s.indexOf(last) > -1)
      {
        if (s.indexOf(first) < s.indexOf(last))
          str = s.substring(s.indexOf(first) + first.length(), s.indexOf(last));        
      }
      meters = parseNMEAFloat(str);
      first = ",M,";
      last  = ",F";
      if (s.indexOf(first) > -1 && s.indexOf(last) > -1)
      {
        if (s.indexOf(first) < s.indexOf(last))
          str = s.substring(s.indexOf(first) + first.length(), s.indexOf(last));        
      }
      fathoms = parseNMEAFloat(str);
    }
    catch (Exception e)
    {
      System.err.println("parseDBT For " + s + ", " + e.toString());
//    e.printStackTrace();
    }

    if (unit == DEPTH_IN_FEET)
      return feet;
    else if (unit == DEPTH_IN_METERS)
      return meters;
    else if (unit == DEPTH_IN_FATHOMS)
      return fathoms;
    else
      return feet;
  }
  
  public static boolean validCheckSum(String sentence)
  {
    return validCheckSum(sentence, false);
  }
  
  public static boolean validCheckSum(String data, boolean verb)
  {
    String sentence = data.trim();
    boolean b = false;    
    try
    {
      int starIndex = sentence.indexOf("*");
      if (starIndex < 0)
        return false;
      String csKey = sentence.substring(starIndex + 1);
      int csk = Integer.parseInt(csKey, 16);
//    System.out.println("Checksum  : 0x" + csKey + " (" + csk + ")");
      String str2validate = sentence.substring(1, sentence.indexOf("*"));
//    System.out.println("To validate:[" + str2validate + "]");
//    char[] ca = str2validate.toCharArray();
//    int calcCheckSum = ca[0];
//    for (int i=1; i<ca.length; i++)
//      calcCheckSum = calcCheckSum ^ ca[i]; // XOR
      
      int calcCheckSum = calculateCheckSum(str2validate);
      b = (calcCheckSum == csk);
//    System.out.println("Calculated: 0x" + lpad(Integer.toString(calcCheckSum, 16).toUpperCase(), 2, "0"));
    }
    catch (Exception ex)
    {
      if (verb) System.err.println("Oops:" + ex.getMessage());
    }
    return b;
  }
  
  public static int calculateCheckSum(String str)
  {
    int cs = 0;
    char[] ca = str.toCharArray();
    cs = ca[0];
    for (int i=1; i<ca.length; i++)
      cs = cs ^ ca[i]; // XOR
    return cs;
  }
  
  /**
   * Enforce the parsing using the Locale.ENGLISH
   * 
   * @param str the string to parse
   * @return the double value
   * @throws Exception, in case it fails
   */
  private static double parseNMEADouble(String str) throws Exception
  {
    NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);      
    Number number = nf.parse(str);
    double d = number.doubleValue();
//  System.out.println("Number is " + Double.toString(d));
    return d;
  }
  
  private static float parseNMEAFloat(String str) throws Exception
  {
    NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);      
    Number number = nf.parse(str);
    float f = number.floatValue();
  //  System.out.println("Number is " + Double.toString(d));
    return f;
  }
  
  /**
   * Parses strings like "2006-05-05T17:35:48.000" + "Z" or UTC Offset like "-10:00"
   *                      01234567890123456789012
   *                      1         2         3
   *                      
   * Return a UTC date                      
   */
  public static long durationToDate(String duration)
  {
    return durationToDate(duration, null);
  }

  /*
   * Sample: "2006-05-05T17:35:48.000Z"
   *          |    |  |  |  |  |  |
   *          |    |  |  |  |  |  20
   *          |    |  |  |  |  17
   *          |    |  |  |  14
   *          |    |  |  11
   *          |    |  8
   *          |    5
   *          0
   */
  public static long durationToDate(String duration, String tz)
          throws RuntimeException
  {
    String yyyy = duration.substring( 0,  4);
    String mm   = duration.substring( 5,  7);
    String dd   = duration.substring( 8, 10);
    String hh   = duration.substring(11, 13);
    String mi   = duration.substring(14, 16);
    String ss   = duration.substring(17, 19);
    String ms   = duration.substring(20, 23);

    float utcOffset = 0F;

    String trailer = duration.substring(19);
    if (trailer.indexOf("+") >= 0 ||
        trailer.indexOf("-") >= 0)
    {
//    System.out.println(trailer);
      if (trailer.indexOf("+") >= 0)
        trailer = trailer.substring(trailer.indexOf("+") + 1);
      if (trailer.indexOf("-") >= 0)
        trailer = trailer.substring(trailer.indexOf("-"));
      if (trailer.indexOf(":") > -1)
      {
        String hours = trailer.substring(0, trailer.indexOf(":"));
        String mins  = trailer.substring(trailer.indexOf(":") + 1);
        utcOffset = (float)Integer.parseInt(hours) + (float)(Integer.parseInt(mins) / 60f);
      }
      else
        utcOffset = Float.parseFloat(trailer);
    }
//  System.out.println("UTC Offset:" + utcOffset);

    Calendar calendar = Calendar.getInstance();
    if (utcOffset == 0f && tz != null)
      calendar.setTimeZone(TimeZone.getTimeZone(tz));
    else
      calendar.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
    try
    {
      calendar.set(Integer.parseInt(yyyy),
                   Integer.parseInt(mm)-1,
                   Integer.parseInt(dd),
                   Integer.parseInt(hh),
                   Integer.parseInt(mi),
                   Integer.parseInt(ss));
      calendar.set(Calendar.MILLISECOND, Integer.parseInt(ms));
    }
    catch (NumberFormatException nfe)
    {
      throw new RuntimeException("durationToDate, for [" + duration + "] : " + nfe.getMessage());
    }
    return calendar.getTimeInMillis() - (long)(utcOffset * (3600 * 1000));
  }

  public static String durationToExcel(String duration)
    throws RuntimeException
  {
    String yyyy = duration.substring( 0,  4);
    String mm   = duration.substring( 5,  7);
    String dd   = duration.substring( 8, 10);
    String hh   = duration.substring(11, 13);
    String mi   = duration.substring(14, 16);
    String ss   = duration.substring(17, 19);
    String result = "";
    try
    {
      result = yyyy + "/" + mm + "/" + dd + " " + hh + ":" + mi + ":" + ss;
    }
    catch (Exception ex)
    {
      throw new RuntimeException("durationToDate, for [" + duration + "] : " + ex.getMessage());
    }
    return result;
  }

  public static void main(String[] args)
  {
    String str = "";
    
    str = "2006-05-05T17:35:48.000Z";
    long ld = durationToDate(str);
    System.out.println(str + " => " + new Date(ld));
    str = "2006-05-05T17:35:48Z";
    ld = durationToDate(str);
    System.out.println(str + " => " + new Date(ld));
    str = "2006-05-05T17:35:48-10:00";
    ld = durationToDate(str);
    System.out.println(str + " => " + new Date(ld));
    str = "2006-05-05T17:35:48+10:00";
    ld = durationToDate(str);
    System.out.println(str + " => " + new Date(ld));
    str = "2006-05-05T17:35:48.000-09:30";
    ld = durationToDate(str);
    System.out.println(str + " => " + new Date(ld));
    
    str = "$IIRMC,092551,A,1036.145,S,15621.845,W,04.8,317,,10,E,A*0D";
    RMC rmc = null;
    rmc = parseRMC(str);
    
    str = "\n     $GPRMC,172214.004,A,3739.8553,N,12222.8144,W,000.0,000.0,220309,,,A*7C  \n  ";
    
    str = "$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A";

    rmc = null; // parseRMC(str);
//  System.out.println("RMC:" + rmc);
//  System.out.println("RMC Done.");

    System.out.println("Lat from RMC :" + getLatFromRMC(str));
    System.out.println("Long from RMC:" + getLongFromRMC(str));
    System.out.println("COG from RMC :" + getCOGFromRMC(str));
    System.out.println("SOG from RMC :" + getSOGFromRMC(str));
    System.out.println("------------------");
    
    str = "$IIMWV,088,T,14.34,N,A*27";
    Wind w = parseMWV(str);
    System.out.println("Wind  :" + w);
    
    str = "$IIRMC,200914.00,A,3749.58,N,12228.33,W,6.90,025,,015,E,N*02";
    System.out.println("Valid:" + validCheckSum(str));
    
    rmc = parseRMC(str);
    System.out.println("RMC:" + rmc);
    
    str = "$IIVWR,148.,L,02.4,N,01.2,M,04.4,K*XX";
    w = parseVWR(str);
    System.out.println("Wind  :" + w);
    
    str = "$IIVTG,054.7,T,034.4,M,005.5,N,010.2,K,A*XX";
    OverGround og = parseVTG(str);
    System.out.println("Over Ground:" + og);
    
    str = "$IIMWV,127.0,R,8.5,N,A*34";
    w = parseMWV(str);
    System.out.println("Wind  :" + w);
    
    str = "$IIVWR,036,R,08.3,N,,,,*6F";
    w = parseVWR(str);
    System.out.println("Wind  :" + w);
    
    str = "$aaVLW,123.45,N,12.34,N*hh";
    double[] d = parseVLW(str);
    System.out.println("Log - Cumul:" + d[StringParsers.LOG_in_VLW] + ", Daily:" + d[StringParsers.DAILYLOG_in_VLW]);
    
    str = "$xxMTW,+18.0,C*hh";
    double t = parseMTW(str);
    System.out.println("Temperature:" + t + "\272C");
    
    str = "$iiRMB,A,0.66,L,003,004,4917.24,N,12309.57,W,001.3,052.5,000.5,V*0B";
    RMB rmb = parseRMB(str);
    System.out.println("RMB:");
    if (rmb != null)
    {
      System.out.println("  XTE:" + rmb.getXte() + " nm (steer " + (rmb.getDts().equals("R")?"Right":"Left") + ")");
      System.out.println("  Origin     :" + rmb.getOwpid());
      System.out.println("  Destination:" + rmb.getDwpid() + " (" + rmb.getDest().toString() + ")");
      System.out.println("  DTD:" + rmb.getRtd() + " nm");
      System.out.println("  BTD:" + rmb.getBtd() + "\272T");
      System.out.println("  STD:" + rmb.getDcv() + " kts");
      System.out.println("  Status:" + (rmb.getAs().equals("V")?"En route":"Done"));
    }
    else
      System.out.println("No RMB Data");
    
    str = "$IIGLL,3739.854,N,12222.812,W,014003,A,A*49";
    Object[] obj = parseGLL(str);
    System.out.println("Position:" + ((GeoPos)obj[GP_in_GLL]).toString() + ", Date:" + ((Date)obj[DATE_in_GLL]).toString());
    
    str = "$IIVTG,311.,T,,M,05.6,N,10.4,K,A*2F";
    og = parseVTG(str);
    System.out.println("Over Ground:" + og);
    
    str = "$IIVTG,,T,295,M,0.0,N,,*02";
    og = parseVTG(str);
    System.out.println("Over Ground:" + og);
    
    str = "$IIDPT,007.4,+1.0,*43";
    float depth = parseDPT(str, DEPTH_IN_METERS);
    System.out.println("Depth:" + depth);
    
    str = "$IIVWR,024,R,08.4,N,,,,*6B";
    w = StringParsers.parseVWR(str);
    
    str = "$IIHDM,125,M*3A";
    int h = parseHDM(str);
    System.out.println("HDM:" + h);
    str = "$IIHDT,131,T*3F";
    h = parseHDT(str);
    System.out.println("HDT:" + h);
    
    str = "$GPZDA,201530.00,04,07,2002,00,00*60";
    UTC utc = parseZDA(str);
    System.out.println("UTC Time: " + utc.toString());
    
    str = "$IIVTG,17.,T,M,7.9,N,,*36";
    og = parseVTG(str);
    System.out.println("Over Ground:" + og);
    
    /*
     * Cloning cable strings
     * 
     $PICOA,90,00,REMOTE,ON*58
     $PICOA,90,02,REMOTE,ON*5A
     $PICOA,90,02,MODE,USB*18
     $PICOA,90,02,MODE,J3E*60
     $PICOA,90,02,RXF,8.415000*05
     $PICOA,90,02,TXF,8.415000*03
     */
    str = "$PICOA,90,00,REMOTE,ON*58";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    
    System.out.println("String generation:");
    str = "PICOA,90,02,RXF,8.415000"; // Attention! No leading '$'
    int cs = StringParsers.calculateCheckSum(str);
    String cks = Integer.toString(cs, 16).toUpperCase();
    if (cks.length() < 2)
      cks = "0" + cks;
    str += ("*" + cks);
    System.out.println("With checksum: $" + str);

    str = "PICOA,90,02,TXF,8.415000";
    cs = StringParsers.calculateCheckSum(str);
    cks = Integer.toString(cs, 16).toUpperCase();
    if (cks.length() < 2)
      cks = "0" + cks;
    str += ("*" + cks);
    System.out.println("With checksum: $" + str);
    
    str = "$IIHDG,178.,,,,*77";
    double[] hdgData = StringParsers.parseHDG(str);
    System.out.println("Hdg:" + hdgData[StringParsers.HDG_in_HDG] + ", d:" + hdgData[StringParsers.DEV_in_HDG] + " W:" + hdgData[StringParsers.VAR_in_HDG]);
    
    str = "$IIHDG,126,,,10,E*16";
    hdgData = StringParsers.parseHDG(str);
    System.out.println("Hdg:" + hdgData[StringParsers.HDG_in_HDG] + ", d:" + hdgData[StringParsers.DEV_in_HDG] + " W:" + hdgData[StringParsers.VAR_in_HDG]);

    str = "$IIRMC,220526.00,A,3754.34,N,12223.20,W,3.90,250,,015,E,N*07";
    rmc = StringParsers.parseRMC(str);
    Date date = rmc.getRmcDate();
    Date time = rmc.getRmcTime();
    
    System.out.println("Date:" + date);
    System.out.println("Time:" + time);
    
    System.out.println("-----------------------------------");
    
    str = "$GPRMC,183333.000,A,4047.7034,N,07247.9938,W,0.66,196.21,150912,,,A*7C";
    rmc = StringParsers.parseRMC(str);
    date = rmc.getRmcDate();
    time = rmc.getRmcTime();
    
    System.out.println("Date:" + date);
    System.out.println("Time:" + time);

    str = "$GPRMC,183334.000,A,4047.7039,N,07247.9939,W,0.61,196.21,150912,,,A*70";
    rmc = StringParsers.parseRMC(str);
    date = rmc.getRmcDate();
    time = rmc.getRmcTime();
    
    System.out.println("Date:" + date);
    System.out.println("Time:" + time);

    System.out.println("------- GSV -------");
    str = "$GPGSV,3,1,11,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00*74";
    Map<Integer, SVData> hm = parseGSV(str);
    if (hm == null)
      System.out.println("GSV wait...");
    str = "$GPGSV,3,2,11,14,25,170,00,16,57,208,39,18,67,296,40,19,40,246,00*74";
    hm = parseGSV(str);
    if (hm == null)
      System.out.println("GSV wait...");
    str = "$GPGSV,3,3,11,22,42,067,42,24,14,311,43,27,05,244,00,,,,*4D";
    hm = parseGSV(str);
    if (hm == null)
      System.out.println("GSV wait...");
    else
    {
      System.out.println(hm.size() + " Satellites in view:");
      for (Integer sn : hm.keySet())
      {
        SVData svd = hm.get(sn);
        System.out.println("Satellite #" + svd.getSvID() + " Elev:" + svd.getElevation() + ", Z:" + svd.getAzimuth() + ", SNR:" + svd.getSnr() + "db");
      }
    }

    System.out.println("------- GGA -------");
    str = "$GPGGA,014457,3739.853,N,12222.821,W,1,03,5.4,1.1,M,-28.2,M,,*7E";
    List<Object> al = parseGGA(str);
    utc = (UTC)al.get(0);
    GeoPos pos = (GeoPos)al.get(1);
    Integer nbs = (Integer)al.get(2);
    Double alt = (Double)al.get(3);
    System.out.println("UTC:" + utc.toString());
    System.out.println("Pos:" + pos.toString());
    System.out.println(nbs.intValue() + " Satellite(s) in use");
    System.out.println("Altitude:" + alt);
    
    str = "$GPGGA,183334.000,4047.7039,N,07247.9939,W,1,6,1.61,2.0,M,-34.5,M,,*6B";
    al = parseGGA(str);
    utc = (UTC)al.get(0);
    pos = (GeoPos)al.get(1);
    nbs = (Integer)al.get(2);
    alt = (Double)al.get(3);
    System.out.println("UTC:" + utc.toString());
    System.out.println("Pos:" + pos.toString());
    System.out.println(nbs.intValue() + " Satellite(s) in use");
    System.out.println("Altitude:" + alt);

    System.out.println("------- GSA -------");
    str = "$GPGSA,A,3,19,28,14,18,27,22,31,39,,,,,1.7,1.0,1.3*35";
    GSA gsa = parseGSA(str);
    System.out.println("- Mode: " + (gsa.getMode1().equals(GSA.ModeOne.Auto)?"Automatic":"Manual"));
    System.out.println("- Mode: " + (gsa.getMode2().equals(GSA.ModeTwo.NoFix)?"No Fix":(gsa.getMode2().equals(GSA.ModeTwo.TwoD)?"2D":"3D")));
    System.out.println("- Sat in View:" + gsa.getSvArray().size());
    System.out.println("-----------");
    str = "$GPGSA,A,2,,,,,,20,23,,,32,,,5.4,5.4,*1F";
    gsa = parseGSA(str);
    System.out.println("- Mode: " + (gsa.getMode1().equals(GSA.ModeOne.Auto)?"Automatic":"Manual"));
    System.out.println("- Mode: " + (gsa.getMode2().equals(GSA.ModeTwo.NoFix)?"No Fix":(gsa.getMode2().equals(GSA.ModeTwo.TwoD)?"2D":"3D")));
    System.out.println("- Sat in View:" + gsa.getSvArray().size());
    
    System.out.println("Test:" + GSA.ModeOne.Auto);
    
    str = "$IIRMC,022136,A,3730.092,N,12228.864,W,00.0,181,141113,15,E,A*1C";
    rmc = parseRMC(str);
    System.out.println("-> RMC date:" + rmc.getRmcDate().toString() + " (" + rmc.getRmcDate().getTime() + ")");
    
    str = "$IIRMC,144432.086,V,,,,,00.0,0.00,190214,,,N*48";
    rmc = parseRMC(str);
    try { System.out.println("-> RMC date:" + rmc.getRmcDate() + " (" + rmc.getRmcDate().getTime() + ")"); }
    catch (Exception ex) { System.out.println("Expected:" + ex.toString()); }
    
    str = "$PGACK,103*40";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    str = "$PGTOP,11,2*6E";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    
    str = "$PMTK010,002*2D";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid........");
    
    str = "$IIMMB,29.9350,I,1.0136,B*78";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    double pressure = parseMMB(str);
    System.out.println(" ==> " + pressure + " hPa");

    str = "$IIMTA,20.5,C*02";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    double temp = parseMTA(str);
    System.out.println(" ==> " + temp + "\272");
    
    str = "$IIXDR,P,1.0136,B,BMP180,C,15.5,C,BMP180*58";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    List<StringGenerator.XDRElement> xdr = parseXDR(str);
    for (StringGenerator.XDRElement x : xdr)
    {
      System.out.println(" => " + x.toString());
    }
    str = "$IIXDR,P,1.0136,B,0,C,15.5,C,1,H,65.5,P,2*6B";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    xdr = parseXDR(str);
    for (StringGenerator.XDRElement x : xdr)
    {
      System.out.println(" => " + x.toString());
      if (x.getTypeNunit().equals(StringGenerator.XDRTypes.HUMIDITY))
      {
        System.out.println("Humidity:" + x.getValue() + "%");
      };
    }
    
    str = "$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A";
    rmc = parseRMC(str);
    try { System.out.println("-> RMC date:" + rmc.getRmcDate() + " (" + rmc.getRmcDate().getTime() + ")"); }
    catch (Exception ex) { System.out.println("Expected:" + ex.toString()); }
    
    str = "$IIRMC,062658,A,1111.464,S,14235.335,W,05.6,226,231110,10,E,A*0A";
    rmc = parseRMC(str);
    try { System.out.println("-> RMC date:" + rmc.getRmcDate() + " (" + rmc.getRmcDate().getTime() + ")"); }
    catch (Exception ex) { System.out.println("Expected:" + ex.toString()); }
    System.out.println("Pos:" + rmc.getGp().lat + "/" + rmc.getGp().lng);

    // A bad one
    str = "$IIRMC,051811,A,3730.079,N,12228.853,W,,,070215,19.905,I,1.013,B,28.8,C,14.0,C,,,,,172.0,T,173.0,M,35.1,N,18.1,M*66";
    rmc = parseRMC(str);
    if (rmc != null)
    {
      try { System.out.println("-> RMC date:" + rmc.getRmcDate() + " (" + rmc.getRmcDate().getTime() + ")"); }
      catch (Exception ex) { System.out.println("Expected:" + ex.toString()); }
    }
    else
      System.out.println("Invalid string:" + str);
    
    str = "$$IIRMC,055549,A,3730.080,N,29.908,I,1.013,B,28.8,C,14.0,C,,,,,169.0,T,170.0,M,28.3,N,14.6,M*67";
    rmc = parseRMC(str);
    if (rmc != null)
    {
      try { System.out.println("-> RMC date:" + rmc.getRmcDate() + " (" + rmc.getRmcDate().getTime() + ")"); }
      catch (Exception ex) { System.out.println("Expected:" + ex.toString()); }
    }
    else
      System.out.println("Invalid string:" + str);
    
    str = "$RPMMB,29.9276,I,1.0133,B*LW,08200,N,000.0,N*59";
    System.out.println("[" + str + "] is " + (validCheckSum(str)?"":"not ") + "valid.");
    pressure = parseMMB(str);
    System.out.println(" ==> " + pressure + " hPa");
    
    System.out.println("Done");
  }    
}