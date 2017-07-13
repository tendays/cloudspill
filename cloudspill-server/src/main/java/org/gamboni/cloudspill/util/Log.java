/**
 * 
 */
package org.gamboni.cloudspill.util;

/**
 * @author tendays
 *
 */
public class Log {

    public static void debug(String message) {
    	warn(message);
    }
    
    public static void warn(String message, Throwable e) {
    	warn(message);
    	e.printStackTrace();
    }

	public static void warn(String message) {
		System.err.println("WARN: "+ message);
	}

	public static void error(String message) {
		System.err.println("ERROR: "+ message);
	}

}
