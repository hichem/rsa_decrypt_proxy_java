package com.boussettahichem.tools;

public class HexDump {

	
	public static String dump(byte data[]) {
		String result = null;
		if (data == null) {
			result = "(null)";
		} else {
			result = String.format("[Data Length: %d]\n", data.length);
			
			int i;
			for(i = 0; i < data.length; i++) {
				result += (((i > 0) && (i % 16 == 0)) ? "\n": "") + String.format("0x%02x ", data[i]);
			}
		}
		
		return result;
	}
}
