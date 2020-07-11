/**
 * 
 */
package org.gamboni.cloudspill.shared.util;

import java.util.Date;

/**
 * @author tendays
 *
 */
public class Log {
	
	private enum Severity {
		DEBUG, INFO, WARN, ERROR
	}

    public static void debug(String message) {
    	log(Severity.DEBUG, message);
    }
    
    public static void info(String message) {
    	log(Severity.INFO, message);
    }
    
    public static void warn(String message, Throwable e) {
    	warn(message);
    	e.printStackTrace();
    }

	public static void warn(String message) {
		log(Severity.WARN, message);
	}

	public static void error(String message, Throwable e) {
		error(message);
		e.printStackTrace();
	}

	public static void error(String message) {
		log(Severity.ERROR, message);
	}

	private static void log(Severity severity, String message) {
		System.err.println(new Date() +" "+ severity +": "+ message);
	}

}
