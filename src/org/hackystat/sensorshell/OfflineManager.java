package org.hackystat.sensorshell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Map;

import org.hackystat.core.kernel.util.DateInfo;
import org.hackystat.core.kernel.util.ExtensionFileFilter;

/**
 * Provides a singleton manager for storage and retrieval of offline sensorshell commands.
 * There are three kinds of clients of OfflineManager: the SensorShell, the various ShellCommand
 * instances, and test framework. Here's how they interact with OfflineManager:
 * <ul>
 *   <li> Each ShellCommand instance has to manually decide when a command should be persisted in
 *   case a later SensorShell.send() call fails. When a ShellCommand processes a command that should
 *   be persisted if send() fails, then it must call
 *   OfflineManager.getInstance().addEntry(timeStamp, ShellCommandName, command, argMap).
 *   
 *   <li> Whenever the SensorShell attempts to send data, it must subsequently call
 *   OfflineManager.getInstance().clear() if the send was successful, or it must call
 *   OfflineManager.getInstance().store() if the send was not successful. Each time store() is
 *   called, a new file named .hackystat/offline/yy.mm.dd.hh.mm.ss.SSS.ser is written out. With
 *   either the clear() or store() methods, the internal list of commands will be cleared.
 *   
 *   <li> When the SensorShell starts up, it will normally attempt to connect to the server via a
 *   ping command, and if the ping succeeds, it will want all of the stored ShellCommands to be
 *   resent to the server. This is accomplished via OfflineManager.getInstance().restore(shell),
 *   which reads in all the serialized data files, invokes SensorShell.doCommand() on each of the
 *   OfflineEntries in each file, and finally deletes the serialized data file.
 *   
 *   <li> The TestOfflineManager class needs to be able to store the test commands in a temporary
 *   directory. The OfflineManager provides the setOfflineDir() method for this purpose. Only the
 *   test class should use this method. The load() method is also made public only for use by tests.
 * </ul>
 *
 * @author    Philip Johnson
 * @version   $Id: OfflineManager.java,v 1.1.1.1 2005/10/20 23:56:44 johnson Exp $
 */
public class OfflineManager {

  /** The singleton instance to be returned by all calls to getInstance(). */
  private static OfflineManager manager = null;

  /** The line separator (carriage return character(s)). */
  private String cr = System.getProperty("line.separator");

  /** The List of OfflineEntry instances coming from the sensors. */
  private ArrayList<OfflineEntry> entryList;

  /** The List of OfflineEntry instances coming from the serialized files. */
  private ArrayList<OfflineEntry> restoreList;

  /** A string containing a summary of the data most recently recovered. */
  private StringBuffer recoverStringBuff = new StringBuffer();

  /** The directory where offline data is stored. */
  private File offlineDir;

  /**
   * Return the (singleton) OfflineManager instance, using the default SensorProperties instantiator
   * if the OfflineManager has not been created yet.
   *
   * @return   The OfflineManager.
   */
  public static OfflineManager getInstance() {
    if (OfflineManager.manager == null) {
      OfflineManager.manager = new OfflineManager();
    }
    return OfflineManager.manager;
  }

  /**
   * Return the (singleton) OfflineManager instance, using the passed File instance to indicate the
   * .hackystat directory if this call results in the instantiation of the singleton OfflineManager.
   * If the OfflineManager instance already exists, it is returned (and thus the passed File
   * instance argument is ignored.) If the passed File is null, then the default location is used.
   *
   * @param hackystatDir  A File instance pointing to the .hackystat directory, or null.
   * @return              The OfflineManager.
   */
  public static OfflineManager getInstance(File hackystatDir) {
    if (OfflineManager.manager == null) {
      OfflineManager.manager = new OfflineManager(hackystatDir);
    }
    return OfflineManager.manager;
  }


  /** Constructor for the singleton OfflineManager object. */
  private OfflineManager() {
    this(new File(System.getProperty("user.home") + "/.hackystat/"));
  }

  /**
   * Constructor for the singleton OfflineManager object in which the offline data storage directory
   * is rooted at hackystatDir. If hackystatDir is null, then the default user.home/.hackystat is
   * used as the hackystatDir.
   *
   * @param hackystatDir  A File instance indicating the directory to be used as the home directory.
   */
  private OfflineManager(File hackystatDir) {
    if (hackystatDir == null) {
      hackystatDir = new File(System.getProperty("user.home") + "/.hackystat/");
    }
    this.entryList = new ArrayList<OfflineEntry>();
    this.restoreList = new ArrayList<OfflineEntry>();
    this.offlineDir = new File(hackystatDir + "/offline/");
    this.offlineDir.mkdirs();
  }


  /**
   * Sets (and if necessary, creates) the directory in which the offline data files should be stored
   * for testing purposes only. Under normal usage, the default directory location should be used.
   * Package-private to restrict access to test class.
   *
   * @param dir  A string containing a fully qualified path to the directory to be used to store the
   *      serialized data files.
   */
  void setOfflineDir(String dir) {
    this.offlineDir = new File(dir);
    this.offlineDir.mkdirs();
  }

  /**
   * Saves this command description for later offline storage if necessary.
   * Regenerates the command line key=value strings from the Map and saves them.
   *
   * @param timeStamp         The timestamp when this command occurred.
   * @param commandShellName  The CommandShell name string ("Activity", etc.)
   * @param command           The command that was invoked ("add", etc.)
   * @param keyValStringMap   A map containing the key-val pairs to be saved.
   */
  public void add(Date timeStamp, String commandShellName, String command, 
      Map<String, String> keyValStringMap) {
    ArrayList<String> argList = new ArrayList<String>();
    argList.add(command);
    for (String key : keyValStringMap.keySet()) {
      String value = keyValStringMap.get(key);
      argList.add(key + "=" + value);
    }
    this.entryList.add(new OfflineEntry(timeStamp, commandShellName, argList));
  }
  
  /**
   * Saves this command description for later offline storage if necessary.
   * Each entry in ArgList must be in the form of key=value.
   *
   * @param timeStamp The timestamp when this command occurred.
   * @param cmdShellName  The CommandShell name string.
   * @param argList The list of arguments to this command, which must start with the
   * command ("add", etc) with the remainder being key-val pairs.
   */
   
  public void add(Date timeStamp, String cmdShellName, List<String> argList) {
    // The alert reader will wonder why I make a new ArrayList below, passing it an ArrayList
    // as an argument.  That's because I want to preserve the abstraction to clients that all
    // we really want to manipulate is some kind of list, while satisfying the requirements of
    // serialization to specify a concrete implementation of List as the data structure
    // in OfflineEntry's.
    this.entryList.add(new OfflineEntry(timeStamp, cmdShellName, new ArrayList<String>(argList)));
  }
  
  /** Clears the internal list of saved commands. */
  void clear() {
    this.entryList.clear();
  }

  /**
   * Stores out the sensor commands to a serialized file in the offline storage area. Upon
   * successful save, clears the list of saved sensor commands.
   * @param shell The sensorshell requesting the data storage. 
   * @return   The File instance that the data was stored in.
   */
  File store(SensorShell shell) {
    String fileStampString = DateInfo.getFileTimestamp(new Date());
    File outFile = new File(this.offlineDir, fileStampString + ".ser");
    try {
      FileOutputStream fileStream = new FileOutputStream(outFile);
      ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
      objectStream.writeObject(this.entryList);
      objectStream.flush();
      objectStream.close();
      this.entryList.clear();
    }
    catch (Exception e) {
      shell.println("Error writing the offline file " + outFile.getName() + " " + e);
    }
    shell.println("Sensor data stored in: " + outFile.getAbsolutePath());
    return outFile;
  }


  /**
   * Finds all cached .ser files containing OfflineEntry instances, reconstructs them, and sends
   * them to the server.  
   * Each .ser file is individually processed, its data recovered, sent to the shell, and then
   * sent to the public server before moving on to the next file.
   * @param shell The SensorShell to which the OfflineEntries will be sent.
   * @return True if offline data was found. 
   */
  @SuppressWarnings("unchecked")
  boolean recover(SensorShell shell) {
    boolean dataFound = false;
    this.recoverStringBuff = new StringBuffer();
    File[] serFiles = this.offlineDir.listFiles(new ExtensionFileFilter(".ser"));
    FileInputStream fileStream = null;
    for (int j = 0; j < serFiles.length; j++) {
      try {
        // Find each OfflineEntry in this file, and send it to the shell.
        shell.println("Recovering offline data from: " + serFiles[j].getName());
        fileStream = new FileInputStream(serFiles[j]);
        ObjectInputStream objectStream = new ObjectInputStream(fileStream);
        // Here's the unchecked type conversion. 
        ArrayList serList = (ArrayList) objectStream.readObject();
        for (Iterator i = serList.iterator(); i.hasNext(); ) {
          OfflineEntry entry = (OfflineEntry) i.next();
          this.recoverStringBuff.append(entry.toString() + cr);
          shell.doCommand(entry.timeStamp, entry.commandShellName, entry.argList);
          dataFound = true;
        }
        fileStream.close();
        // Send the data associated with each .ser file to avoid overflow.
        shell.send();
      }
      catch (Exception e) {
        shell.println("Error recovering offline data from: " + serFiles[j] + " " + e);
        try {
          fileStream.close();
        }
        catch (Exception f) {
          // do nothing
        }
      }
      // Delete the serialized file, regardless of whether it was restored correctly or not.
      serFiles[j].delete();
    }   
    return dataFound;
  }
  

  /**
   * Returns a string summarizing the contents of the data recovered from offline.
   *
   * @return   The recover info string.
   */
  String getRecoverInfoString() {
    return this.recoverStringBuff.toString();
  }

  /**
   * Returns a string containing the current contents of the restoreList.
   *
   * @return   The string version of the current state of the restore list.
   */
  String getRestoreListString() {
    String cr = System.getProperty("line.separator");
    StringBuffer buff = new StringBuffer();
    buff.append("[");
    for (Iterator i = this.restoreList.iterator(); i.hasNext(); ) {
      OfflineEntry entry = (OfflineEntry) i.next();
      buff.append(entry.toString());
      if (i.hasNext()) {
        buff.append(cr);
      }
    }
    buff.append("]");
    return buff.toString();
  }


  /**
   * Provides a command line interface for inspecting the contents of an offline ser file. Must be
   * called with one argument, the .ser file containing offline commands. Prints out the contents
   * but does not otherwise affect the file.
   *
   * @param args  One argument, a string containing the path to a .ser file.
   * @exception Exception  if an error occurs
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    OfflineManager manager = new OfflineManager();
    FileInputStream fileStream = new FileInputStream(new File(args[0]));
    ObjectInputStream objectStream = new ObjectInputStream(fileStream);
    manager.restoreList = (ArrayList)objectStream.readObject();
    System.out.println(manager.getRestoreListString());
  }
}

