package org.hackystat.sensorshell;

import java.io.FileFilter;
import java.io.File;
import java.util.Locale;

/**
 * Provides a file filter that accepts only files with a given case-insensitive extension.
 *
 * @author    Philip M. Johnson
 * @version   $Id: ExtensionFileFilter.java,v 1.1.1.1 2005/10/20 23:56:44 johnson Exp $
 */
public class ExtensionFileFilter implements FileFilter {
  /** The wanted extension. */
  private String extension;

  /**
   * Creates a file filter that accepts only files with the given extension, case-insensitive.
   *
   * @param extension  The extension string (typically including the ".").
   */
  public ExtensionFileFilter(String extension) {
    this.extension = extension.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Determines if the passed file should be filtered or not.
   *
   * @param file  The file to be (potentially) filtered.
   * @return      True if the file has the specified extension, false otherwise.
   */
  public boolean accept(File file) {
    if (file.isDirectory()) {
      return false;
    }
    return file.getName().toLowerCase(Locale.ENGLISH).endsWith(this.extension);
  }
}


