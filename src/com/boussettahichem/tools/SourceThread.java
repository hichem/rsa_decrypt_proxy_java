package com.boussettahichem.tools;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class SourceThread extends Thread {
	
	
	//Constants
	final int IN_BUFFER_LENGTH				= 4096;
	final int DATA_HEADER_LENGTH			= 2;
	
	//Member Variables
	Socket					_srcSocket;
	RetryProxy				_retryProxy;
	ByteArrayOutputStream	_sourceData;
	boolean					_shouldKeepRunning;
	
	//Constructor
	public SourceThread(RetryProxy proxy, Socket srcSocket) {
		Logger.getSharedLogger().log("SourceThread::SourceThread");
		
		_srcSocket			= srcSocket;
		_retryProxy			= proxy;
		_sourceData			= new ByteArrayOutputStream();
		_shouldKeepRunning	= true;
	}
	
	//
	public void run() {
		Logger.getSharedLogger().log("SourceThread::run");
		
		//Read incoming data and process it
		byte[] inBuffer		= new byte[IN_BUFFER_LENGTH];
		int bytesRead 		= 0;
		_shouldKeepRunning	= true;
		
		while(_shouldKeepRunning) {
			try {
				bytesRead = _srcSocket.getInputStream().read(inBuffer);
			} catch (IOException e) {
				e.printStackTrace();
				Logger.getSharedLogger().log("SourceThread::run [Error Reading Data from Source]");
				
				//Check if the source socket is closed
				if (_srcSocket.isConnected() == false) {
					_shouldKeepRunning = false;
					
					Logger.getSharedLogger().log("SourceThread::run [Socket Closed - Stopping the thread]");
				}
			}
			
			//Bufferize the received data
			if (bytesRead > 0) {
				_sourceData.write(inBuffer, 0, bytesRead);
				
				//Log the data read
				Logger.getSharedLogger().log(String.format("SourceThread::run [Src -> Proxy] %s", HexDump.dump(Arrays.copyOfRange(inBuffer, 0, bytesRead))));
				
				//Process the received data - Extract the request and send them to destination
				processSourceData();
			}
			
			try {
				sleep(10);	//Sleep 10 milliseconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Logger.getSharedLogger().log("SourceThread::run [SourceThread Ended]");
	}
	
	void processSourceData() {
		//Logger.getSharedLogger().log("SourceThread::processSourceData");
		
		int 		expectedDataLength 	= 0;
		int 		totalDataLength 	= 0;
		byte[]		bytes				= _sourceData.toByteArray();
		
		//Get the source's requests one by one
		while (bytes.length > DATA_HEADER_LENGTH) {
			expectedDataLength = ((bytes[0] + 256) % 256) * 256 + (bytes[1] + 256) % 256;
			totalDataLength = DATA_HEADER_LENGTH + expectedDataLength;
			
			if (bytes.length >= totalDataLength) {
				//Send the request
				byte[] tmp = Arrays.copyOfRange(bytes, DATA_HEADER_LENGTH, totalDataLength);
				_retryProxy.writeDataToDestination(tmp);
				
				//Remove the request data from the buffer
				_sourceData.reset();
				_sourceData.write(bytes, totalDataLength, bytes.length - totalDataLength);
				bytes = _sourceData.toByteArray();
			} else {
				break;
			}
		}
	}
	
	public void stopThread() {
		Logger.getSharedLogger().log("SourceThread::stopThread");
		
		_shouldKeepRunning = false;
	}
}
