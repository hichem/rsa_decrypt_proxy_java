package com.boussettahichem.tools;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
	
	//Shared Logger Instance
	private static Logger g_sharedLogger = null;
	
	private Logger() {
		
	}
	
	public void log(String message) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss:SSS");
		Date now = new Date();
		String currentTimeToString = dateFormatter.format(now);
		
		//Log time & message
		System.out.println(String.format("[%s]%s", currentTimeToString, message));
	}
	
	static public Logger getSharedLogger() {
		if (g_sharedLogger == null) {
			g_sharedLogger = new Logger();
		}
		
		return g_sharedLogger;
	}
}
