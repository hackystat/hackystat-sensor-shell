package org.hackystat.sensorshell.command;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.SingleSensorShell;

/**
 * Implements the Ping command, which ensures that the SensorBase is reachable. 
 * @author Philip Johnson
 */
public class PingCommand extends Command {
  
  /** The timeout in milliseconds, initialized from sensorshell.properties.*/
  private int timeout;
  
  /**
   * Creates the PingCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties. 
   */
  public PingCommand(SingleSensorShell shell, SensorShellProperties properties) {
    super(shell, properties);
    // don't use properties.getTimeout() * 1000, since it could be set quite high for MultiShell.
    this.timeout = 5000; 
  }
  
  /**
   * Does a ping on the hackystat server and returns true if the server was
   * accessible. A ping-able server indicates the data will be sent to it,
   * while a non-pingable server indicates that data will be stored offline.
   *
   * @return   True if the server could be pinged.
   */
  public boolean isPingable() {
    return this.isPingable(this.timeout);
  }

  /**
   * Does a ping on the hackystat server and returns true if the server was
   * accessible. A ping-able server indicates the data will be sent to it,
   * while a non-pingable server indicates that data will be stored offline.
   * If the server is not reachable, or does not respond with given time frame, false will
   * be returned.
   *
   * @param timeout Maximum seconds to wait for server response. A 0 value or negative
   * value is equivalent to set time out to infinity.
   * @return   True if the server could be pinged.
   */
  public boolean isPingable(int timeout) {
    boolean result = false;
    int waitTime = timeout <= 0 ? 0 : timeout;
    this.shell.println("Starting a ping...");
    PingWorkerThread workThread = new PingWorkerThread(this.host, this.email, this.password);
    workThread.start();
    try {
      workThread.join(waitTime);  //block this thread until work thread dies or times out.
    }
    catch (InterruptedException ex) {
      //do nothing
    }
    //if work thread is still alive, then it's time out, result = false by default.
    if (!workThread.isAlive()) {
      result = workThread.serverPingable;
    }
    this.shell.println("Finished the ping... result is: " + result);
    return result;
  }

  /**
   * Worker thread to ping the server to determine whether it's reachable or not. The original
   * ping command is implemented as a synchronous command, a separate thread is need to
   * implement time out feature.
   *
   * @author Qin ZHANG
   */
  private static class PingWorkerThread extends Thread {

    /**
     * This instance will only be be accessed in the parent thread after the termination of this
     * thread, there is no need to synchronize access.
     */
    private boolean serverPingable = false;
    private String host;
    private String email;
    private String password;

    /**
     * Constructs this worker thread.
     * @param host The sensorbase host.
     * @param email The client email.
     * @param password The client password.
     */
    public PingWorkerThread(String host, String email, String password) {
      setDaemon(true); //want VM to terminate even if this thread is alive.
      this.host = host;
      this.email = email;
      this.password = password;
    }

    /** Pings the server synchronously. */
    @Override
    public void run() {
      this.serverPingable = SensorBaseClient.isRegistered(this.host, this.email, this.password);
    }
  }

}
