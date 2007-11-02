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
  private SensorShell shell;
  
  private JAXBContext jaxbContext;
  
  /**
   * Constructor for OfflineManager which initializes the location for offline data.
   * @param shell The SensorShell associated with this OfflineManager.
   */
  public OfflineManager(SensorShell shell) {
    this.offlineDir = new File(System.getProperty("user.home") + "/.hackystat/offline/");
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
      }
    catch (Exception e) {
      shell.println("Error writing the offline file " + outFile.getName() + " " + e);
    }
    shell.println("Stored " + sensorDatas.getSensorData().size() + " sensor data instances in: " 
        + outFile.getAbsolutePath());
  }
  
  /**
   * Recovers any previously stored SensorDatas instances from their serialized files.
   * Each SensorDatas instance is deserialized, then each SensorData instance inside is
   * individually sent to the SensorShell.  This gives the SensorShell an opportunity to 
   * send batches off at whatever interval it chooses.
   * All serialized files are deleted after being processed, regardless of whether or not
   * processing is successful or not. 
   */
  public void recover() {
    File[] xmlFiles = this.offlineDir.listFiles(new ExtensionFileFilter(".xml"));
    FileInputStream fileStream = null;
    for (int i = 0; i < xmlFiles.length; i++) {
      try {
        // Reconstruct the SensorDatas instances from the serialized files. 
        this.shell.println("Recovering offline data from: " + xmlFiles[i].getName());
        fileStream = new FileInputStream(xmlFiles[i]);
        Unmarshaller unmarshaller = this.jaxbContext.createUnmarshaller();
        SensorDatas sensorDatas = (SensorDatas)unmarshaller.unmarshal(fileStream);
        for (SensorData data : sensorDatas.getSensorData()) {
          this.shell.add(data);
        }
        fileStream.close();
      }
      catch (Exception e) {
        this.shell.println("Error recovering offline data from: " + xmlFiles[i] + " " + e);
        try {
          fileStream.close();
        }
        catch (Exception f) { //NOPMD
          // do nothing
        }
      }
      // Delete the serialized file, regardless of whether it was restored correctly or not.
      xmlFiles[i].delete();
    }   
  }
}
