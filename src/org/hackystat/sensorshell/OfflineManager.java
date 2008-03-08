package org.hackystat.sensorshell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDatas;
import org.hackystat.utilities.home.HackystatUserHome;

/**
 * Provides a facility for persisting buffered SensorData instances locally when the SensorBase
 * host is not available and then sending them during a subsequent invocation of SensorShell.
 * 
 * @author Philip Johnson
 */
public class OfflineManager {
  
  /** The directory where offline data is stored. */
  private File offlineDir;
  
  /** The SensorShell associated with this OfflineManager. */
  private SingleSensorShell shell;
  /** The jaxb context. */
  private JAXBContext jaxbContext;
  
  /** Whether or not data has been stored offline. */
  boolean hasOfflineData = false;
  
  /**
   * Constructor for OfflineManager which initializes the location for offline data.
   * @param shell The SensorShell associated with this OfflineManager.
   */
  public OfflineManager(SingleSensorShell shell) {
    this.offlineDir = new File(HackystatUserHome.getHome(),  "/.hackystat/sensorshell/offline/");
    this.offlineDir.mkdirs();
    this.shell = shell;
    try {
      this.jaxbContext = 
        JAXBContext.newInstance(
            org.hackystat.sensorbase.resource.sensordata.jaxb.ObjectFactory.class);
    }
    catch (Exception e) {
      throw new RuntimeException("Could not create JAXB context.", e);
    }
  }
  
  /**
   * Stores a SensorDatas instance to a serialized file in the offline directory.
   * @param sensorDatas The SensorDatas instance to be stored. 
   */
  public void store(SensorDatas sensorDatas) {
    SimpleDateFormat fileTimestampFormat = 
      new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS", Locale.US);
    String fileStampString = fileTimestampFormat.format(new Date());
    File outFile = new File(this.offlineDir, fileStampString + ".xml");
    try {
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      marshaller.marshal(sensorDatas, new FileOutputStream(outFile));
      shell.println("Stored " + sensorDatas.getSensorData().size() + " sensor data instances in: " 
          + outFile.getAbsolutePath());
      this.hasOfflineData = true;
      }
    catch (Exception e) {
      shell.println("Error writing the offline file " + outFile.getName() + " " + e);
    }
  }
  
  /**
   * Returns true if this offline manager has successfully stored any data offline.  
   * @return True if offline data has been stored. 
   */
  public boolean hasOfflineData() {
    return this.hasOfflineData;
  }
  
  /**
   * Recovers any previously stored SensorDatas instances from their serialized files.
   * Each SensorDatas instance is deserialized, then each SensorData instance inside is
   * individually sent to the SensorShell.  This gives the SensorShell an opportunity to 
   * send batches off at whatever interval it chooses.
   * All serialized files are deleted after being processed, regardless of whether or not
   * processing is successful or not. 
   * @throws SensorShellException If problems occur sending the recovered data.
   */
  public void recover() throws SensorShellException {
    File[] xmlFiles = this.offlineDir.listFiles(new ExtensionFileFilter(".xml"));
    FileInputStream fileStream = null;
    List<SensorData> offlineData = new ArrayList<SensorData>(500);
    for (int i = 0; i < xmlFiles.length; i++) {
      try {
        // Reconstruct the SensorDatas instances from the serialized files. 
        this.shell.println("Recovering offline data from: " + xmlFiles[i].getName());
        fileStream = new FileInputStream(xmlFiles[i]);
        Unmarshaller unmarshaller = this.jaxbContext.createUnmarshaller();
        SensorDatas sensorDatas = (SensorDatas)unmarshaller.unmarshal(fileStream);
        // Now that we have the sensor data in memory, close and delete the file. 
        fileStream.close();
        xmlFiles[i].delete();
        // Next, add this data to our local arraylist.
        for (SensorData data : sensorDatas.getSensorData()) {
          offlineData.add(data);
        }
        // Now, if we've collected enough local entries, send them out.
        if (offlineData.size() > 500) {
          this.shell.println("Sending recovered offline data (" + offlineData.size() + " entries)");
          for (SensorData data : offlineData) {
            shell.add(data);
          }
          offlineData.clear();
        }
      }
      catch (Exception e) {
        this.shell.println("Error recovering offline data from: " + xmlFiles[i] + " " + e);
        try {
          fileStream.close();
        }
        catch (Exception f) { 
          this.shell.println("Failed to close: " + fileStream.toString() + " " + e);
        }
      }
    }
    // Clear out any remaining data from our local buffer. 
    for (SensorData data : offlineData) {
      shell.add(data);
    }
    this.shell.println("Explicit send of recovered data.");
    shell.send();
  }
}
