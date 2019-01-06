package com.boussettahichem.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;

public class RSADecryptor {

	
	//Constants
	static final String				ALGORITHM						= "RSA";
	
	//Static Variables
	private static RSADecryptor 		g_sharedRSADecryptor 		= null;
	
	//Member Variables
	private PrivateKey					_privateRSAKey				= null;
	private int							_privateRSAKeyLength		= 0;
	private Cipher						_decryptor					= null;
	
	
	//Private Constructor
	private RSADecryptor () {
		_privateRSAKey			= null;
		
		//Initialize the decryptor
		try {
			_decryptor			= Cipher.getInstance(ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		}
	}
	
	//Shared Constructor
	static public RSADecryptor getSharedRSADecryptor() {
		if (g_sharedRSADecryptor == null) {
			g_sharedRSADecryptor = new RSADecryptor();
		}
		return g_sharedRSADecryptor;
	}
	
	
	//Member methods
	public boolean setPrivateRSAKey(byte[] key) {
		Logger.getSharedLogger().log("RSADecryptor::setPrivateRSAKey");
		
		KeyFactory				keyFactory		= null;
		PKCS8EncodedKeySpec		privateKeySpec	= null;
		boolean					result			= false;
		PEMReader 				pemReader		= null;
		byte[] 					pkcs8Key		= null;
		
		if (key != null) {
			try {
				//Process the private key
				keyFactory		= KeyFactory.getInstance(ALGORITHM);
				try {
					pemReader		= new PEMReader(key);
					pkcs8Key		= pemReader.getDerBytes();
					privateKeySpec	= new PKCS8EncodedKeySpec(pkcs8Key);
					
					try {
						_privateRSAKey	= keyFactory.generatePrivate(privateKeySpec);
						//_privateRSAKeyLengthprivateRSA = _privateRSAKey.;
						
						Logger.getSharedLogger().log("RSADecryptor::setPrivateRSAKey [RSA Key Processed Successfully]");
						
						//Initialize the decryptor
						try {
							_decryptor.init(Cipher.DECRYPT_MODE, _privateRSAKey);
							
							//At this point, everything worked fine
							result = true;
						} catch (InvalidKeyException e) {
							Logger.getSharedLogger().log("RSADecryptor::setPrivateRSAKey [Failed to initialize the decryptor with private key]");
							e.printStackTrace();
						}
					} catch (InvalidKeySpecException e) {
						Logger.getSharedLogger().log("RSADecryptor::setPrivateRSAKey [Failed to initialize the private key]");
						e.printStackTrace();
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public byte[] decrypt(byte[] cipher) {
		Logger.getSharedLogger().log(String.format("RSADecryptor::decrypt | Cipher: %s", HexDump.dump(cipher)));
		
		byte[] 					decrypted			= null;
		byte[] 					block				= new byte[32];
		ByteArrayInputStream	inByteArrayStream	= null;
		ByteArrayOutputStream	outByteArrayStream	= new ByteArrayOutputStream();
		CipherInputStream		cipherInStream		= null;
		int						bytesRead			= 0;
		
		while ((cipher != null) && (cipher.length >= 256)) {
			inByteArrayStream	= new ByteArrayInputStream(Arrays.copyOfRange(cipher, 0, 256));
			cipherInStream		= new CipherInputStream(inByteArrayStream, _decryptor);
			
			//Decrypt
			//Read blocks and decrypt
			try {
				while ((bytesRead = cipherInStream.read(block)) > 0) {
					outByteArrayStream.write(block, 0, bytesRead);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			cipher = Arrays.copyOfRange(cipher, 256, cipher.length);
		}
		
		decrypted = outByteArrayStream.toByteArray();
		
		//Log the decrypted Message
		Logger.getSharedLogger().log(String.format("RSADecryptor::decrypt | Decrypted: %s", HexDump.dump(decrypted)));
		
		return decrypted;
	}
}
