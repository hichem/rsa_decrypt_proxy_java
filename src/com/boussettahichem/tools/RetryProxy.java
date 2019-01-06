package com.boussettahichem.tools;

import java.awt.HeadlessException;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RetryProxy {

	//User Defined Types
	enum  RetryErrors {
		RetryErrorNoError,
		RetryErrorNullSequenceNumber,
		RetryErrorWrongSequenceNumber
	};
	
	//Constants & Defaults
	final int			DEFAULT_RETRY_COUNT				= 2;
	final int 			DEFAULT_SOURCE_PORT				= 9530;
	final int 			DEFAULT_DESTINATION_PORT		= 9530;
	final int 			DEFAULT_WAIT_FOR_REPLY_TIMEOUT	= 10;
	final int 			DATA_HEADER_LENGTH				= 2;
	
	//Member variables
	String				_sourceIP;
	String				_destinationIP;
	int					_sourcePort;
	int					_destinationPort;
	boolean				_useNetworkRetry;
	ServerSocket		_serverSocket;
	Socket				_sourceSocket;
	Socket				_destinationSocket;
	boolean				_isServerStarted;
	int					_retryCount;
	int 				_waitForReplyTimeout;
	SourceThread		_sourceThread;
	DestinationThread	_destinationThread;
	ExecutorService		_retryLogicExecutor;
	int					_currentSequenceNumber;
	Condition			_waitForReplyCondition;
	Lock				_waitForReplyLock;
	RetryErrors			_currentRetryError;
	byte[]				_privateKey;
	boolean				_useDecryption;
	
	
	
	//Constructor
	public RetryProxy() {
		Logger.getSharedLogger().log("RetryProxy::RetryProxy");
		
		//Variable Initialization
		_sourceIP					= "";
		_destinationIP				= "";
		_sourcePort					= DEFAULT_SOURCE_PORT;
		_destinationPort			= DEFAULT_DESTINATION_PORT;
		_useNetworkRetry			= false;
		_serverSocket				= null;
		_sourceSocket				= null;
		_destinationSocket			= null;
		_isServerStarted			= false;
		_retryCount					= DEFAULT_RETRY_COUNT;
		_waitForReplyTimeout		= DEFAULT_WAIT_FOR_REPLY_TIMEOUT;
		_sourceThread				= null;
		_destinationThread			= null;
		_retryLogicExecutor			= Executors.newSingleThreadExecutor();
		_currentSequenceNumber		= 0;
		_waitForReplyLock			= new ReentrantLock();
		_waitForReplyCondition 		= _waitForReplyLock.newCondition();
		_currentRetryError			= RetryErrors.RetryErrorNoError;
		_privateKey					= null;
		_useDecryption				= false;
	}
	
	
	//Properties
	public void setDestinationIP(String dstIP) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setDestinationIP [%s]", dstIP));
		_destinationIP = dstIP;
	}
	
	public void setSourcePort(int srcPort) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setSourcePort [%d]", srcPort));
		if (srcPort > 0) {
			_sourcePort = srcPort;
		}
	}
	
	public void setDestinationPort(int dstPort) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setDestinationPort [%d]", dstPort));
		if (dstPort > 0) {
			_destinationPort = dstPort;
		}
	}
	
	public void setUseNetworkRetry(boolean enableRetry) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setUseNetworkRetry [%s]", ((enableRetry) ? "yes" : "no")));
		_useNetworkRetry = enableRetry;
	}
	
	public void setRetryCount(int count) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setRetryCount [%d]", count));
		if (count > 0) {
			_retryCount = count;
		}
	}
	
	public void setWaitForReplyTimeout(int timeout) {
		Logger.getSharedLogger().log(String.format("RetryProxy::setWaitForReplyTimeout [timeout = %d]", timeout));
		if(timeout > 0) {
			_waitForReplyTimeout = timeout;
		}
	}
	
	public void setPrivateKey(byte[] key) {
		Logger.getSharedLogger().log("RetryProxy::setPrivateKey");
		_privateKey = key;
		
		//Initialize the RSA Decryptor
		if (RSADecryptor.getSharedRSADecryptor().setPrivateRSAKey(_privateKey)) {
			Logger.getSharedLogger().log("RetryProxy::setPrivateKey [Proxy is now using decryption]");
			
			_useDecryption = true;
		} else {
			Logger.getSharedLogger().log("RetryProxy::setPrivateKey [Proxy failed to set decryption mode]");
			
			_useDecryption = false;
		}
	}
	
	public String getSourceIP() {
		Logger.getSharedLogger().log("RetryProxy::getSourceIP");
		return _sourceIP;
	}
	
	public String getDestinationIP() {
		Logger.getSharedLogger().log("RetryProxy::getDestinationIP");
		return _destinationIP;
	}
	
	public int getSourcePort() {
		Logger.getSharedLogger().log("RetryProxy::getSourcePort");
		return _sourcePort;
	}
	
	public int getDestinationPort() {
		Logger.getSharedLogger().log("RetryProxy::getDestinationPort");
		return _destinationPort;
	}
	
	public int getRetryCount() {
		Logger.getSharedLogger().log("RetryProxy::getRetryCount");
		return _retryCount;
	}
	
	public int getWaitForReplyTimeout() {
		Logger.getSharedLogger().log("RetryProxy::getWaitForReplyTimeout");
		return _waitForReplyTimeout;
	}
	
	public byte[] getPrivateKey() {
		Logger.getSharedLogger().log("RetryProxy::getPrivateKey");
		return _privateKey;
	}
	
	//Public Functions
	public synchronized boolean runServer() {
		Logger.getSharedLogger().log("RetryProxy::runServer");
		
		boolean result = true;
		
		if (_isServerStarted == false) {
			// Create a ServerSocket to listen for connections with
		    try {
				_serverSocket = new ServerSocket(_sourcePort);
			} catch (IOException e) {
				e.printStackTrace();
				
				result = false;
				Logger.getSharedLogger().log("RetryProxy::runServer [Failed to initialize Server Socket]");
				
				return false;
			}
		    
		    //Set the server as started
		    _isServerStarted = true;
		    Logger.getSharedLogger().log(String.format("RetryProxy::runServer [Server Started on %s:%d]", _serverSocket.getInetAddress().getHostAddress(), _sourcePort));
		    
		    do {
		    	Socket tmpClient = null;
			    
			    //Wait for a client to connect
			    try {
					tmpClient = _serverSocket.accept();
				} catch (IOException e) {
					e.printStackTrace();
					
					Logger.getSharedLogger().log("RetryProxy::runServer [Failed to Accept Connection]");
				}
			    
			    //If a source is already connected, disconnect it and stop the source thread
			    if (_sourceSocket != null) {
		    		Logger.getSharedLogger().log("RetryProxy::runServer [The server received a new connection attempt - The current client will be disconnected]");
					_disconnectSource();
					_disconnectDestination();
			    }
			    
		    	_sourceSocket 	= tmpClient;
		    	_sourceIP		= tmpClient.getInetAddress().getHostAddress();
		    	Logger.getSharedLogger().log(String.format("RetryProxy::runServer [Connection Accepted for %s]", _sourceIP));
		    	
		    	//Reset the sequence number used for retry logic
		    	if (_useNetworkRetry) {
		    		_initializeSequenceNumber();
		    	}
		    	
		    	//Connect to destination
		    	_connectToDestination();
		    		
		    	//Start SourceThread
				_sourceThread = new SourceThread(this, _sourceSocket);
				_sourceThread.start();
		    } while (true);
			
		} else {
			Logger.getSharedLogger().log("RetryProxy::runServer [Server Already Started]");
		}
	    
		
		return result;
	}
	
	public synchronized void stopServer() {
		Logger.getSharedLogger().log("RetryProxy::stopServer");
	}
	
	public boolean getUseNetworkRetry() {
		Logger.getSharedLogger().log("RetryProxy::getUseNetworkRetry");
		return _useNetworkRetry;
	}
	
	public void writeDataToDestination(byte[] data) {
		Logger.getSharedLogger().log("RetryProxy::writeDataToDestination");
		
		_retryLogicExecutor.submit(new SourceToDestinationWriteOperation(data));
	}
	
	
	public void writeDataToSource(byte[] data) {
		Logger.getSharedLogger().log("RetryProxy::writeDataToSource");
		
		byte[]				tmpBuffer			= null;
		byte[]				outBuffer			= null;
		
		//Check if retry logic is enabled
		if (_useNetworkRetry) {
			//Get the sequence number
			int returnedSequenceNumber = data[0];
			tmpBuffer = Arrays.copyOfRange(data, 1, data.length);
			
			//Signal the wait for reply condition
			_waitForReplyLock.lock();
			_waitForReplyCondition.signal();
			
			//Check for retry errors
			if ((returnedSequenceNumber != _currentSequenceNumber) && (returnedSequenceNumber == 0)) {
				
				//Turn on the reset sequence number flag so that the current request is sent again with a null sequence number
				_currentRetryError = RetryErrors.RetryErrorNullSequenceNumber;
				
				//Return without writing the data to source - Release the lock protecting the condition
				_waitForReplyLock.unlock();
				return;
			} else if (returnedSequenceNumber != _currentSequenceNumber) {
				Logger.getSharedLogger().log(String.format("RetryProxy::writeDataToSource [The sequence number of the response (%d) does not match the one used in the request (%d)]", returnedSequenceNumber, _currentSequenceNumber));
				
				_currentRetryError = RetryErrors.RetryErrorWrongSequenceNumber;
				
				//Return without writing the data to source - Release the lock protecting the condition
				_waitForReplyLock.unlock();
				return;
			}
			
			_currentRetryError = RetryErrors.RetryErrorNoError;
			
			//Release the lock protecting the condition
			_waitForReplyLock.unlock();
		} else {
			tmpBuffer = data;
		}
		
		//Use decryption when applicable
		if (_useDecryption) {
			tmpBuffer = RSADecryptor.getSharedRSADecryptor().decrypt(tmpBuffer);
		}
		
		//Check if response is valid
		if ((tmpBuffer == null) || (tmpBuffer.length == 0)) {
			//Return an invalid response {0x00, 0x00}
			tmpBuffer = new byte[] {0x00, 0x00};
		}
		
		//Allocate memory for message
		outBuffer = new byte[DATA_HEADER_LENGTH + tmpBuffer.length];
		
		//Prepare the data
		outBuffer[0] = (byte)(tmpBuffer.length / 256);
		outBuffer[1] = (byte)(tmpBuffer.length % 256);
		try {
			System.arraycopy(tmpBuffer, 0, outBuffer, 2, tmpBuffer.length);
		} catch (ArrayIndexOutOfBoundsException ex) {
			ex.printStackTrace();
		}
		
		//Write the data
		try {
			if ((_sourceSocket != null) && (_sourceSocket.isConnected())) {
				_sourceSocket.getOutputStream().write(outBuffer);
				
				//Log the written data to console
				Logger.getSharedLogger().log(String.format("RetryProxy::writeDataToSource [Proxy -> Src]%s", HexDump.dump(outBuffer)));
			} else {
				Logger.getSharedLogger().log("RetryProxy::writeDataToSource [Failed to write data - Source Disconnected]");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//Private Functions
	
	boolean _connectToDestination() {
		Logger.getSharedLogger().log("RetryProxy::_connectToDestination");
		
		boolean result = true;
		
		try {
			_destinationSocket = new Socket(_destinationIP, _destinationPort);
			
			Logger.getSharedLogger().log(String.format("RetryProxy::_connectToDestination [Connected to %s:%d]", _destinationIP, _destinationPort));
			
			//Run Destination Thread
			_destinationThread = new DestinationThread(this, _destinationSocket);
			_destinationThread.start();
		} catch (ConnectException ex) {
			Logger.getSharedLogger().log(String.format("RetryProxy::_connectToDestination [Failed to connect to %s:%d]", _destinationIP, _destinationPort));
			
			_destinationSocket = null;
			result = false;
		} catch (IOException e) {
			e.printStackTrace();
			
			_destinationSocket = null;
			result = false;
		}
		
		return result;
	}
	
	
	void _scheduledWriteDataToDestination(byte[] data) {
		Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination");
		
		byte[]				outBuffer			= null;
		int					outBufferLength		= 0;
		int					dataLength			= 0;
		int 				retryNumber			= 0;
		Socket				_ptrSourceSocket	= _sourceSocket; 
		
		//Prepare the request
		if (_useNetworkRetry) {
			//Update the sequence number each time a new request is received from the source - sequence number is needed for retry logic
			_updateSequenceNumber();
			
			dataLength			= data.length + 1;		//The sequence number is added at the beginning of the request
			outBufferLength		= dataLength + DATA_HEADER_LENGTH;
			outBuffer			= new byte[outBufferLength];
			outBuffer[0]		= (byte)(dataLength / 256);
			outBuffer[1]		= (byte)(dataLength % 256);
			outBuffer[2]		= (byte)_currentSequenceNumber;
			System.arraycopy(data, 0, outBuffer, DATA_HEADER_LENGTH + 1, dataLength - 1);
		} else {
			dataLength			= data.length;
			outBufferLength 	= dataLength + DATA_HEADER_LENGTH;
			outBuffer			= new byte[outBufferLength];
			outBuffer[0]		= (byte)(dataLength / 256);
			outBuffer[1]		= (byte)(dataLength % 256);
			System.arraycopy(data, 0, outBuffer, DATA_HEADER_LENGTH, dataLength);
		}
		
		do {
			//Check the source has not disconnected
			if ((_ptrSourceSocket != _sourceSocket) || (_ptrSourceSocket.isConnected() == false)) {
				Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Source Socket Disconnected: Exiting Task]");
				break;
			}
			
			//Write the data
			try {
				//Connect to destination
				_connectToDestination();
				
				if ((_destinationSocket != null) && (_destinationSocket.isConnected())) {
					_destinationSocket.getOutputStream().write(outBuffer);
					
					//Log the written data to console
					Logger.getSharedLogger().log(String.format("RetryProxy::_scheduledWriteDataToDestination [Proxy -> Dst]%s", HexDump.dump(outBuffer)));
				} else {
					Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Destination Socket Disconnected]");
				}
			} catch (IOException e) {
				e.printStackTrace();
				
				Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Failed to write the data on output stream]");
				
				//Check if the destination socket is still connected
				if (_destinationSocket.isConnected() == false) {
					Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Destination Socket Disconnected]");
					_disconnectDestination();
				}
			}
			
			if (_useNetworkRetry) {
				//Wait for the reply
				_waitForReplyLock.lock();
				try {
					if (_waitForReplyCondition.await(_waitForReplyTimeout, TimeUnit.SECONDS) == true) {		//The condition is signaled
						
						//Check for errors
						if (_currentRetryError == RetryErrors.RetryErrorNoError) {
							Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Received Valid Response - Ending Scheduled Task]");
							
							//Unlock the condition and exit the loop
							_waitForReplyLock.unlock();
							break;
						} else if (_currentRetryError == RetryErrors.RetryErrorNullSequenceNumber) {
							//Reset the sequence number
							_resetSequenceNumber();
							
							Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Received a null sequence number - Will reset current sequence number and send the request again]");
						} else if (_currentRetryError == RetryErrors.RetryErrorWrongSequenceNumber) {
							Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Received a wrong sequence number - Will send the request again]");
						}
					}
					//Increment the retry number
					retryNumber++;
					
					if (retryNumber <= _retryCount) {
						Logger.getSharedLogger().log(String.format("RetryProxy::_scheduledWriteDataToDestination [Retry #%d]", retryNumber));
					}
					
					//Reconnect to destination
					//_connectToDestination();
					_waitForReplyLock.unlock();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		} while ((_useNetworkRetry) && (retryNumber <= _retryCount));
		
		if ((_useNetworkRetry) && (retryNumber == _retryCount)) {
			Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Failed to write the data to destination - No response is received]");
		}
		
		Logger.getSharedLogger().log("RetryProxy::_scheduledWriteDataToDestination [Task Ended]");
	}
	
	//Sequence Number Management
	
	void _initializeSequenceNumber() {
		Logger.getSharedLogger().log("RetryProxy::_initializeSequenceNumber");
		
		_currentSequenceNumber = -1;
	}
	
	void _resetSequenceNumber() {
		Logger.getSharedLogger().log("RetryProxy::_resetSequenceNumber");
		
		_currentSequenceNumber = 0;
	}
	
	void _updateSequenceNumber() {
		Logger.getSharedLogger().log(String.format("RetryProxy::_updateSequenceNumber [Sequence Number = %d]", _currentSequenceNumber + 1));
		
		_currentSequenceNumber++;
		
		if (_currentSequenceNumber == 256) {
			_currentSequenceNumber = 1;
		}
	}
	
	synchronized void _disconnectSource() {
		Logger.getSharedLogger().log("RetryProxy::_disconnectSource");
		
		try {
			_sourceSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
			
			Logger.getSharedLogger().log("RetryProxy::_disconnectSource [Failed to close the source socket - Socket may have been already closed]");
		}
		
		//Stop Source Thread
		if (_sourceThread != null) {
			_sourceThread.stopThread();
		}
		
		try {
			_sourceThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		_sourceSocket = null;
	}
	
	synchronized void _disconnectDestination() {
		Logger.getSharedLogger().log("RetryProxy::_disconnectDestination");
		
		try {
			_destinationSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
			
			Logger.getSharedLogger().log("RetryProxy::_disconnectSource [Failed to close the destination socket - Socket may have been already closed]");
		}
		
		//Stop Destination Thread
		if (_destinationThread != null) {
			_destinationThread.stopThread();
		}
		
		try {
			_destinationThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		_destinationSocket = null;
	}
	
	
	//Operation Class
	
	class SourceToDestinationWriteOperation implements Runnable {

		byte[] _sourceData = null;
		
		public SourceToDestinationWriteOperation (byte[] sourceData) {
			_sourceData = sourceData;
		}
		
		public void run() {
			_scheduledWriteDataToDestination(_sourceData);
		}
		
	}
}
