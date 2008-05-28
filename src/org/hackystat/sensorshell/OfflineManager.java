package org.hackystat.sensorshell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDatas;
import org.hackystat.utilities.home.HackystatUserHome;

/**
 * Provides a facility for: (a) persisting buffered SensorData instances locally when the SensorBase
 * host is not available and (b) recovering them during a subsequent invocation of SensorShell.
 * 
 * @author Philip Johnson
 */
public class OfflineManager {
  
  /** The directory where offline data is stored. */
  private File offlineDir;
  
  /** The jaxb context. */
  private JAXBContext jaxbContext;
  
  /** Holds the sensorShellProperties instance from the parent sensor shell. */
  private SensorShellProperties properties; 
  
  /** The shell that created this offline manager. **/
  private SingleSensorShell shell; 
  
  /** The tool that was created the parent shell. */
  private String tool; 
  
  /** Whether or not data has been stored offline. */
  boolean hasOfflineData = false;
  
  /**
   * Creates an OfflineManager given the parent shell and the tool. 
   * @param shell The parent shell.
   * @param tool The tool. 
   */
  public OfflineManager(SingleSensorShell shell, String tool) {
    this.shell = shell;
    this.properties = shell.getProperties();
    this.tool = tool;
    this.offlineDir = new File(HackystatUserHome.getHome(),  "/.hackystat/sensorshell/offline/");
    this.offlineDir.mkdirs();
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
   * Creates a new sensorshell instance to do the sending. 
   * Each SensorDatas instance is deserialized, then each SensorData instance inside is
   * individually sent to the SensorShell.  This gives the SensorShell an opportunity to 
   * send batches off at whatever interval it chooses.
   * All serialized files are deleted after being processed if successful.
   * @throws SensorShellException If problems occur sending the recovered data.
   */
  public void recover() throws SensorShellException {
    // Create a new properties instance with offline recovery/storage disabled. 
    SensorShellProperties props = SensorShellProperties.getOfflineMode(this.properties);
    // Provide a separate log file for this offline recovery. 
    String offlineTool = this.tool + "-offline-recovery";
    SingleSensorShell shell = new SingleSensorShell(props, false, offlineTool);
    File[] xmlFiles = this.offlineDir.listFiles(new ExtensionFileFilter(".xml"));
    shell.println("Invoking offline recovery on " + xmlFiles.length + " files.");
    FileInputStream fileStream = null;

    for (int i = 0; i < xmlFiles.length; i++) {
      try {
        // Reconstruct the SensorDatas instances from the serialized files. 
        shell.println("Recovering offline data from: " + xmlFiles[i].getName());
        fileStream = new FileInputStream(xmlFiles[i]);
        Unmarshaller unmarshaller = this.jaxbContext.createUnmarshaller();
        SensorDatas sensorDatas = (SensorDatas)unmarshaller.unmarshal(fileStream);
        // Now that we have the sensor data in memory, close and delete the file. 
        fileStream.close();
        boolean isDeleted = xmlFiles[i].delete();
        shell.println(xmlFiles[i].getName() + "was deleted: " + isDeleted);
        // Next, add this data to our local shell.
        shell.println("About to add " + sensorDatas.getSensorData().size() + " instances.");
        for (SensorData data : sensorDatas.getSensorData()) {
          shell.add(data);
        }
      }
      catch (Exception e) {
        shell.println("Error recovering offline data from: " + xmlFiles[i] + " " + e);
        try {
          fileStream.close();
        }
        catch (Exception f) { 
          shell.println("Failed to close: " + fileStream.toString() + " " + e);
        }
      }
    }
  }
}
