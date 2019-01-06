package com.boussettahichem.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class App {

	
	public static void main(String[] args) {
		
		/*
		  Args[0]: Src Port
		  Args[1]: Dst IP
		  Args[2]: Dst Port
		  Args[3]: Retry (yes or no)
		  Args[4]: Number of retries
		  Args[5]: Retry Timeout
		  Args[6]: Private Key file path
		*/
		
		//Check Args
		if (args.length < 3) {
			System.out.println("There are missing arguments");
			System.out.println("usage: java -jar DecryptionProxy.jar srcPort dstIP srcIP [yes/no] [retries] [timeout] [privKey]");
			System.out.println("       srcPort: the local port on which the proxy will listen");
			System.out.println("       dstIP: the remote host's IP address");
			System.out.println("       dstPort: the remote host's port number");
			System.out.println("       yes/no: if yes, the tunnel will use a retry logic to try reconnecting to the remote host if the connection is lost. If no, the retry logic won't be used. This parameter is optional.");
			System.out.println("       retries: number of retries. This parameter is optional. If not specified, a retry number of 2 is used");
			System.out.println("       timeout: the retry timeout in seconds, which is the time to wait until a response is received. This parameter is optional and defaults to 10 seconds");
			System.out.println("       privKey: The path to an RSA private key if data decryption should be enabled in the proxy. If this parameter is omitted, decryption is disabled");
			return;
		}
		
		int 		srcPort 		= Integer.parseInt(args[0]);
		String		dstIP			= args[1];
		int			dstPort			= Integer.parseInt(args[2]);
		boolean		useRetryLogic	= false;
		int			retryCount		= 0;
		int 		retryTimeout	= 0;
		String		privKeyPath		= "";
		byte[]		privKey			= null;
		
		if (args.length >= 4) {
			useRetryLogic = ((args[3].equalsIgnoreCase("yes") ? true : false));
		}
		
		if (args.length >= 5) {
			retryCount = Integer.parseInt(args[4]);
		}
		
		if (args.length >= 6) {
			retryTimeout = Integer.parseInt(args[5]);
		}
		
		if (args.length >= 7) {
			privKeyPath = args[6];
			
			//Load the key from file
			try {
				File 				keyFile			= new File(privKeyPath);
				FileInputStream		keyFileStream	= new FileInputStream(keyFile);
				
				privKey = new byte[(int)keyFile.length()];
				keyFileStream.read(privKey);
				keyFileStream.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				
				Logger.getSharedLogger().log(String.format("App::main [Private Key File Not Found At Path: %s]", privKeyPath));
			} catch (IOException e) {
				e.printStackTrace();
				Logger.getSharedLogger().log("App::main [I/O Error when reading the private key from file]");
			}
		}
		
		
		//Initialize the proxy and start it
		RetryProxy proxy = new RetryProxy();
		
		proxy.setSourcePort(srcPort);
		proxy.setDestinationIP(dstIP);
		proxy.setDestinationPort(dstPort);
		proxy.setUseNetworkRetry(useRetryLogic);
		proxy.setRetryCount(retryCount);
		proxy.setWaitForReplyTimeout(retryTimeout);
		proxy.setPrivateKey(privKey);
		
		proxy.runServer();
	}

}
