package com.boussettahichem.tools;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;


public class DestinationThread extends Thread {

	//Constants
	final int IN_BUFFER_LENGTH				= 4096;
	final int DATA_HEADER_LENGTH			= 2;
	
	//Member Variables
	Socket					_dstSocket;
	RetryProxy				_retryProxy;
	ByteArrayOutputStream	_destinationData;
	boolean					_shouldKeepRunning;
	
	public DestinationThread(RetryProxy proxy, Socket dstSocket) {
		Logger.getSharedLogger().log("SourceThread::DestinationThread");
		
		_dstSocket			= dstSocket;
		_retryProxy			= proxy;
		_destinationData	= new ByteArrayOutputStream();
		_shouldKeepRunning	= true;
	}
	
	public void run() {
		Logger.getSharedLogger().log("DestinationThread::run");
		
		//Read incoming data and process it
		byte[] inBuffer 	= new byte[IN_BUFFER_LENGTH];
		int bytesRead 		= 0;
		_shouldKeepRunning	= true;
		
		while(_shouldKeepRunning) {
			try {
				bytesRead = _dstSocket.getInputStream().read(inBuffer);
			} catch (IOException e) {
				e.printStackTrace();
				Logger.getSharedLogger().log("DestinationThread::run [Error Reading Data from Destionation]");
				
				//Check if the stream is still open
				if (_dstSocket.isConnected() == false) {
					_shouldKeepRunning = false;
					
					Logger.getSharedLogger().log("DestinationThread::run [Socket Closed - Stopping the thread]");
				}
			}
			
			//Bufferize the received data
			if (bytesRead > 0) {
				_destinationData.write(inBuffer, 0, bytesRead);
				
				//Log the data read
				Logger.getSharedLogger().log(String.format("DestinationThread::run [Dst -> Proxy] %s", HexDump.dump(Arrays.copyOfRange(inBuffer, 0, bytesRead))));
				
				//Process the received data - Extract the request and send them to destination
				processDestinationData();
			}
			
			try {
				sleep(10);	//Sleep 10 milliseconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Logger.getSharedLogger().log("DestinationThread::run [Thread Ended]");
	}
	
	void processDestinationData() {
		//Logger.getSharedLogger().log("DestinationThread::processDestinationData");
		
		int 		expectedDataLength 	= 0;
		int 		totalDataLength 	= 0;
		byte[]		bytes				= _destinationData.toByteArray();
		
		//Get the source's requests one by one
		while (bytes.length > DATA_HEADER_LENGTH) {
			expectedDataLength = ((bytes[0] + 256) % 256) * 256 + (bytes[1] + 256) % 256;
			totalDataLength = DATA_HEADER_LENGTH + expectedDataLength;
			
			if (bytes.length >= totalDataLength) {
				//Send the request
				_retryProxy.writeDataToSource(Arrays.copyOfRange(bytes, DATA_HEADER_LENGTH, totalDataLength));
				
				//Remove the request data from the buffer
				_destinationData.reset();
				_destinationData.write(bytes, totalDataLength, bytes.length - totalDataLength);
				bytes = _destinationData.toByteArray();
			} else {
				break;
			}
		}
	}
	
	public void stopThread() {
		Logger.getSharedLogger().log("DestinationThread::stopThread");
		
		_shouldKeepRunning = false;
	}
}
