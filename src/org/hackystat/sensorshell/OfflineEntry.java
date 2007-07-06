package org.hackystat.sensorshell;

import java.io.Serializable;
import java.util.Date;
import java.util.ArrayList;
import java.util.Iterator;

import org.hackystat.core.kernel.util.DateInfo;

/**
 * Provides a representation of the data to be saved to the serialized offline file. 
 * Since this thing is just a package-private utility structure, you can access the fields directly.
 * I won't comment on the pain and suffering I went through
 * with Checkstyle in order to allow myself to do this. Oh wait, I just did.
 *
 * @author    Philip Johnson
 * @version   $Id: OfflineEntry.java,v 1.1.1.1 2005/10/20 23:56:44 johnson Exp $
 */
@SuppressWarnings("serial")
class OfflineEntry implements Serializable {
  /** The time stamp indicating when this command actually took place. */
  Date timeStamp;
  /** The ShellCommand name. */
  String commandShellName;
  /** The actual commands to the ShellCommand. */
  ArrayList argList;

  /**
   * The constructor to make instances of this puppy.
   *
   * @param timeStamp         The timestamp for this entry.
   * @param commandShellName  The commandShell name.
   * @param argList           The arguments.
   */
  public OfflineEntry(Date timeStamp, String commandShellName, ArrayList argList) {
    this.timeStamp = timeStamp;
    this.commandShellName = commandShellName;
    this.argList = argList;
  }

  /**
   * A string representation of this entry.
   *
   * @return   The string representation.
   */
  public String toString() {
    StringBuffer returnString = new StringBuffer();
    returnString.append(DateInfo.shortFormat(this.timeStamp));
    returnString.append("#");
    returnString.append(this.commandShellName);
    returnString.append("#");
    for (Iterator i = argList.iterator(); i.hasNext(); ) {
      returnString.append((String) i.next());
      if (i.hasNext()) {
        returnString.append("#");
      }
    }
    return returnString.toString();
  }
}

