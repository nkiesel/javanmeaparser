package ocss.nmea.ais;

import java.io.BufferedReader;

import java.io.FileReader;

import ocss.nmea.parser.StringParsers;

public class AISParser
{
  public final static boolean verbose = false;
  /*
   * !AIVDM,1,1,,A,15NB>cP03jG?l`<EaV0`MFO000S>,0*39
   * ^      ^ ^  ^ ^                            ^ ^
   * |      | |  | |                            | NMEA Checksum
   * |      | |  | |                            End of message
   * |      | |  | Encoded AIS Data
   * |      | |  AIS Channel (A or B)
   * |      | Sentence Number
   * |      Number of sentences
   * NMEA Message type, for AIS
   */
  
  /*
   * See http://gpsd.berlios.de/AIVDM.html
   * 
AIS Message Type 1:
  1-6     Message Type
  7-8     Repeat Indicator
  9-38    userID (MMSI)
  39-42   Navigation Satus
  43-50   Rate of Turn (ROT)
  51-60   Speed Over Ground (SOG)
  61-61   Position Accuracy
  62-89   Longitude
  90-116  latitude
  117-128 Course Over Ground (COG)
  129-137 True Heading (HDG)
  138-143 Time Stamp (UTC Seconds)
  144-146 Regional RESERVED
  147-148 Spare
  149-149 Receiver Autonomous Integrity Monitoring (RAIM)
  149-151 SOTDMA Sync State
  152-154 SOTDMA Slot Timeout
  155-168 SOTDMA Slot Offset

AIS Message type 2:
  1-6    Message Type
  7-8    Repeat Indicator
  9-38   userID (MMSI)
  39-42  Navigation Satus
  43-50  Rate of Turn (ROT)
  51-60  Speed Over Ground (SOG)
  61-61  Position Accuracy
  62-89  Longitude
  90-116 latitude
  117-128 Course Over Ground (COG)
  129-137 True Heading (HDG)
  138-143 Time Stamp (UTC Seconds)
  144-146 Regional RESERVED
  147-148 Spare
  149-149 Receiver Autonomous Integrity Monitoring (RAIM)
  149-151 SOTDMA Sync State
  152-154 SOTDMA Slot Timeout
  155-168 SOTDMA Slot Offset
   */
  
  public enum AISData
  {
    MESSAGE_TYPE    (  0,   6, "Message Type"),
    REPEAT_INDICATOR(  6,   8, "Repeat Indicator"),
    MMSI            (  8,  38, "userID (MMSI)"),
    NAV_STATUS      ( 38,  42, "Navigation Status"),
    ROT             ( 42,  50, "Rate of Turn"),
    SOG             ( 50,  60, "Speed Over Ground"),
    POS_ACC         ( 60,  61, "Position Accuracy"),
    LONGITUDE       ( 61,  89, "Longitude"),
    LATITUDE        ( 89, 116, "Latitude"),
    COG             (116, 128, "Course Over Ground"),
    HDG             (128, 137, "True Heading"),
    TIME_STAMP      (137, 143, "Time Stamp (UTC Seconds)");

    @SuppressWarnings("compatibility:-6815213573434389704")
    private static final long serialVersionUID = 1L;

    private final int from;          // start offset    
    private final int to;            // end offset
    public final String description; // Description
    
    AISData(int from, int to, String desc)
    {
      this.from = from;
      this.to   = to;
      this.description= desc;
    }
    
    public int from() { return from; }
    public int to() { return to; }
    public String description() { return description; }
  }
    
  public final static String AIS_PREFIX = "!AIVDM";
  public final static int PREFIX_POS       = 0;
  public final static int NB_SENTENCES_POS = 1;
  public final static int AIS_DATA_POS     = 5;
  
  public static AISRecord parseAIS(String sentence) throws Exception
  {
    boolean valid = StringParsers.validCheckSum(sentence);
    if (!valid)
      throw new RuntimeException("Invalid AIS Data (Bad checksum) for [" + sentence + "]");
    
    String[] dataElement = sentence.split(",");
    if (!dataElement[PREFIX_POS].equals(AIS_PREFIX))
      throw new RuntimeException("Unmanaged AIS Prefix [" + dataElement[PREFIX_POS] + "].");
     
    if (!dataElement[NB_SENTENCES_POS].equals("1")) // More than 1 message: Not Managed
      return null; 
               
    AISRecord aisRecord = new AISRecord(System.currentTimeMillis()); 
    String aisData = dataElement[AIS_DATA_POS];
//  System.out.println("[" + aisData + "]");
    String binString = encodedAIStoBinaryString(aisData);
//  System.out.println(binString);
    
    for (AISData a : AISData.values())
    {
      String binStr = binString.substring(a.from(), a.to());
      int intValue = Integer.parseInt(binStr, 2);
      if (a.equals(AISData.LATITUDE) || a.equals(AISData.LONGITUDE))
      {
         if ((a.equals(AISData.LATITUDE) && intValue != (91 * 600000) && intValue > (90 * 600000)) || 
             (a.equals(AISData.LONGITUDE) && intValue != (181 * 600000) && intValue > (180  * 600000)))
         {
           intValue = - Integer.parseInt(neg(binStr), 2);
         }
      }
      else if (a.equals(AISData.ROT))
      {
        if (intValue > 128)
          intValue = - Integer.parseInt(neg(binStr), 2);
      }
      setAISData(a, aisRecord, intValue);
      if (verbose)
        System.out.println(a + " [" + binStr + "] becomes [" + intValue + "]");
    }
    return aisRecord;
  }
  
  /**
   * 2's complement, for negative numbers.
   * 
   * @param binStr
   * @return
   */
  private static String neg(String binStr)
  {
    String s = "";
    for (int i=0; i<binStr.length(); i++)
      s += (binStr.charAt(i)=='0'?'1':'0');
    return s;
  }
  
  private static void setAISData(AISData a, AISRecord ar, int value)
  {
    if (a.equals(AISData.MESSAGE_TYPE))
      ar.setMessageType(value);
    else if (a.equals(AISData.REPEAT_INDICATOR))
      ar.setRepeatIndicator(value);
    else if (a.equals(AISData.MMSI))
      ar.setMmsi(value);
    else if (a.equals(AISData.NAV_STATUS))
      ar.setNavstatus(value);
    else if (a.equals(AISData.ROT))
      ar.setRot(value);
    else if (a.equals(AISData.SOG))
      ar.setSog(value);
    else if (a.equals(AISData.POS_ACC))
      ar.setPosAcc(value);
    else if (a.equals(AISData.LONGITUDE))
      ar.setLongitude(value);
    else if (a.equals(AISData.LATITUDE))
      ar.setLatitude(value);
    else if (a.equals(AISData.COG))
      ar.setCog(value);
    else if (a.equals(AISData.HDG))
      ar.setHdg(value);
    else if (a.equals(AISData.TIME_STAMP))
      ar.setUtc(value);
  }
  
  private static String encodedAIStoBinaryString(String encoded)
  {
    StringBuffer sb = new StringBuffer();
    for (int i=0; i<encoded.length(); i++)
    {
      int c = encoded.charAt(i);
      c -= 48;
      if (c > 40)
        c -= 8;
      String bin = lPad(Integer.toBinaryString(c), "0", 6);
      sb.append(bin);
      if (verbose)
        System.out.println(encoded.charAt(i) + " becomes " + bin + " (" + c + ")");
//    sb.append(" ");
    }
    return sb.toString();
  }
  
  private static String lPad(String s, String pad, int len)
  {
    String str = s;
    while (str.length() < len)
      str = pad + str;
    return str;
  }
  
  public static class AISRecord
  {
    private int messageType;
    private int repeatIndicator;
    private int mmsi;
    private int navstatus;
    private int rot;
    private float sog;
    private int posAcc;
    private float longitude;
    private float latitude;
    private float cog;
    private int hdg;
    private int utc;
    private long recordTimeStamp;

    public AISRecord(long now)
    {
      super();
      recordTimeStamp = now;
    }
    
    public void setMessageType(int messageType)
    {
      this.messageType = messageType;
    }

    public int getMessageType()
    {
      return messageType;
    }

    public void setRepeatIndicator(int repeatIndicator)
    {
      this.repeatIndicator = repeatIndicator;
    }

    public int getRepeatIndicator()
    {
      return repeatIndicator;
    }

    public void setMmsi(int mmsi)
    {
      this.mmsi = mmsi;
    }

    public int getMmsi()
    {
      return mmsi;
    }

    public void setNavstatus(int navstatus)
    {
      this.navstatus = navstatus;
    }

    public int getNavstatus()
    {
      return navstatus;
    }

    public void setRot(int rot)
    {
      this.rot = rot;
    }

    public int getRot()
    {
      return rot;
    }

    public void setSog(int sog)
    {
      this.sog = (sog / 10f);
    }

    public float getSog()
    {
      return sog;
    }

    public void setPosAcc(int posAcc)
    {
      this.posAcc = posAcc;
    }

    public int getPosAcc()
    {
      return posAcc;
    }

    public void setLongitude(int longitude)
    {
      this.longitude = (longitude / 600000f);
    }

    public float getLongitude()
    {
      return longitude;
    }

    public void setLatitude(int latitude)
    {
      this.latitude = (latitude / 600000f);
    }

    public float getLatitude()
    {
      return latitude;
    }

    public void setCog(int cog)
    {
      this.cog = (cog / 10f);
    }

    public float getCog()
    {
      return cog;
    }

    public void setHdg(int hdg)
    {
      this.hdg = hdg;
    }

    public int getHdg()
    {
      return hdg;
    }

    public void setUtc(int utc)
    {
      this.utc = utc;
    }

    public int getUtc()
    {
      return utc;
    }
    
    public static String decodeStatus(int stat)
    {
      String status = "";
      switch (stat)
      {
        case 0:
          status = "Under way using engine";
          break;
        case 1:
          status = "At anchor";
          break;
        case 2:
          status = "Not under command";
          break;
        case 3:
          status = "Restricted manoeuverability";
          break;
        case 4:
          status = "Constrained by her daught";
          break;
        case 5:
          status = "Moored";
          break;
        case 6:
          status = "Aground";
          break;
        case 7:
          status = "Engaged in fishing";
          break;
        case 8:
          status = "Under way sailing";
          break;
        case 9:
          status = "Reserved for future...";
          break;
        case 10:
          status = "Reserved for future...";
          break;
        case 11:
          status = "Reserved for future...";
          break;
        case 12:
          status = "Reserved for future...";
          break;
        case 13:
          status = "Reserved for future...";
          break;
        case 14:
          status = "Reserved for future...";
          break;
        case 15:
        default:
          status = "Not defined";
          break;
      }
      return status;
    }
    
    public String toString()
    {
      String str = "";
      str = "Type:" + messageType + ", Repeat:" + repeatIndicator + ", MMSI:" + mmsi + ", status:" + decodeStatus(navstatus) + ", rot:" + rot +
            ", Pos:" + latitude + "/" + longitude + " (Acc:" + posAcc + "), COG:" + cog + ", SOG:" + sog + ", HDG:" + hdg;
      
      return str;
    }

    public void setRecordTimeStamp(long recordTimeStamp)
    {
      this.recordTimeStamp = recordTimeStamp;
    }

    public long getRecordTimeStamp()
    {
      return recordTimeStamp;
    }
  }
  
  public static void main_(String[] args) throws Exception
  {
    String ais;
    if (args.length > 0)
      System.out.println(parseAIS(args[0]));
    else
    {    
      ais = "!AIVDM,1,1,,A,14eG;o@034o8sd<L9i:a;WF>062D,0*7D";
      System.out.println(parseAIS(ais));
  
      ais = "!AIVDM,1,1,,A,15NB>cP03jG?l`<EaV0`MFO000S>,0*39";
      System.out.println(parseAIS(ais));
      
      ais = "!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5C";
      System.out.println(parseAIS(ais));
    }
  }
  
  public static void main(String[] args) throws Exception
  {
    String dataFileName = "nils.nmea"; // "nmea.dump.txt" , "ais.txt"
    if (args.length > 0)
      dataFileName = args[0];
    
    BufferedReader br = new BufferedReader(new FileReader(dataFileName));
    String line = "";
    while (line != null)
    {
      line = br.readLine();
      if (line != null)
      {
        if (!line.startsWith("#") && line.startsWith("!"))
        {
          try
          {
            System.out.println(parseAIS(line));
          }
          catch (Exception ex)
          {
            System.err.println("For [" + line + "], " + ex.toString());
          }
        }
      }
    }
    br.close();
  }
}
