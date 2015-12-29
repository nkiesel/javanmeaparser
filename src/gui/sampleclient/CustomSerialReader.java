package gui.sampleclient;

import java.io.IOException;

import gnu.io.SerialPortEvent;

import ocss.nmea.api.NMEAReader;
import ocss.nmea.api.NMEAEvent;
import java.util.ArrayList;

import gnu.io.PortInUseException;
import gnu.io.NoSuchPortException;
import gnu.io.CommPort;
import gnu.io.UnsupportedCommOperationException;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.InputStream;
import java.util.Enumeration;

import java.util.TooManyListenersException;

import gnu.io.CommPortOwnershipListener;
import gnu.io.SerialPortEventListener;

import java.util.List;

import ocss.nmea.api.NMEAListener;

public class CustomSerialReader 
     extends NMEAReader 
  implements SerialPortEventListener,
             CommPortOwnershipListener
{
  String comPort = "COM1";
  
  public CustomSerialReader()
  {
  }
  
  public CustomSerialReader(String com)
  {
    comPort = com;
  }
  
  public CustomSerialReader(List<NMEAListener> al)
  {
    super(al);
  }

  public CustomSerialReader(List<NMEAListener> al, String com)
  {
    super(al);
    comPort = com;
  }

  InputStream theInput = null;

  public void read()
  {
    if (System.getProperty("verbose", "false").equals("true")) System.out.println("From " + this.getClass().getName() + " Reading Serial Port " + comPort);
    super.enableReading();
    // Opening Serial port COM1
    Enumeration enumeration = CommPortIdentifier.getPortIdentifiers();
    int nbp = 0;
    while (enumeration.hasMoreElements())
    {
      CommPortIdentifier cpi = (CommPortIdentifier)enumeration.nextElement();
      System.out.println("Port:" + cpi.getName());
      nbp++;
    }
    System.out.println("Found " + nbp + " port(s)");
    
    CommPortIdentifier com = null;
    try { com = CommPortIdentifier.getPortIdentifier(comPort); }
    catch (NoSuchPortException nspe)
    {
      System.err.println("No Such Port");
      nspe.printStackTrace();
      return;
    }
    CommPort thePort = null;
    try 
    { 
      com.addPortOwnershipListener(this);
      thePort = com.open("NMEAPort", 10000); 
    }
    catch (PortInUseException piue)
    {
      System.err.println("Port In Use");
      return;
    }
    int portType = com.getPortType();
    if (portType == CommPortIdentifier.PORT_PARALLEL)
      System.out.println("This is a parallel port");
    else if (portType == CommPortIdentifier.PORT_SERIAL)
      System.out.println("This is a serial port");
    else
      System.out.println("This is an unknown port:" + portType);
    if (portType == CommPortIdentifier.PORT_SERIAL)
    {
      SerialPort sp = (SerialPort)thePort;
      try
      { sp.addEventListener(this); }
      catch (TooManyListenersException tmle)
      {
        sp.close();
        System.err.println(tmle.getMessage());
        return;
      }
      sp.notifyOnDataAvailable(true);
      try 
      {
        sp.enableReceiveTimeout(30);
      } 
      catch (UnsupportedCommOperationException ucoe) 
      {
        sp.close();
        System.err.println(ucoe.getMessage());
        return;
      }
      try
      {
        // Settings for B&G Hydra, TackTick, NKE, most of the NMEA Stations.
        sp.setSerialPortParams(4800,
                               SerialPort.DATABITS_8,
                               SerialPort.STOPBITS_1, 
                               SerialPort.PARITY_NONE);
      }
      catch (UnsupportedCommOperationException ucoe)
      {
        System.err.println("Unsupported Comm Operation");
        return;
      }
      try
      {
        sp.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
        theInput = sp.getInputStream();
        System.out.println("Reading serial port...");
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
    // Reading on Serial Port
    System.out.println("Port is open...");
  }

  public void serialEvent(SerialPortEvent serialPortEvent)
  {
    switch (serialPortEvent.getEventType())
    {
      case SerialPortEvent.DATA_AVAILABLE:
        if (canRead())
        {
          try
          {
            StringBuffer inputBuffer = new StringBuffer();
            int newData = 0;
            while (newData != -1)
            {
              try
              {
                if (theInput != null)
                {
                  newData = theInput.read();
                  if (newData == -1)
                    break;
                  inputBuffer.append((char) newData);
                }
              }
              catch (IOException ex)
              {
                System.err.println(ex);
                return;
              }
            }
            String s = new String(inputBuffer);
            // Display the read string
            boolean justDump = false;
            if (justDump)
              System.out.println(":: [" + s + "] ::");
            else
              super.fireDataRead(new NMEAEvent(this, s));
          }
          catch (Exception ex)
          {
            ex.printStackTrace();                  
          }
        }
//      else
//      {
//        System.out.println("Stop Reading serial port.");
//      }
      default:
        break;
    }
  }

  public void ownershipChange(int type)
  {
    if (type == CommPortOwnershipListener.PORT_OWNERSHIP_REQUESTED) 
    {
      System.out.println("PORT_OWNERSHIP_REQUESTED");
    }
    else
      System.out.println("ownership changed:" + type);
  }
  
  public static void main(String[] args)
  {
    new CustomSerialReader().read();
  }
  
}
