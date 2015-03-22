## NMEA Parser for Java ##
  * Provide Java API to read and parse an NMEA stream.
  * The stream can be any kind of stream, Serial Port being one of them.
  * It reads the stream as an Observer (Design Pattern), and then notifies subscribers when an NMEA sentence is completed and available.
  * Sentence Parsers are also available, to turn the NMEA strings into Objects usable from Java.
  * Include Checksum validation of the NMEA Sentences.
  * Some possibilities to parse AIS Sentences are now included (Feb-2013).

<br><br>
This project is part of the Navigation Desktop project. Build it from <a href='http://code.google.com/p/oliv-soft-project-builder/'>there</a>.