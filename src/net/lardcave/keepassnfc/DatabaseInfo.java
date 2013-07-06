package net.lardcave.keepassnfc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

import android.content.Context;

/* Represents the on-disk database info including encrypted password */

public class DatabaseInfo {
	public String database;
	public String keyfile_filename;
	public String password;
	public int config;
	
	public DatabaseInfo(String database, String keyfile_filename, String password, int config)
	{
		this.database = database;
		this.keyfile_filename = keyfile_filename;
		this.password = password;
		this.config = config;
	}
	
	private byte[] encrypt_password(byte[] encryption_bytes, int offset)
	{
		int i;
		int crypted_idx = 0;
		byte[] crypted_password = new byte[Settings.password_length];
		byte[] plaintext_password = password.getBytes();
		SecureRandom rng = new SecureRandom();		
		
		// Password length...
		crypted_password[crypted_idx ++] = (byte)password.length();
		// ... and password itself...
		for (i = 0; i < plaintext_password.length; i++) 
			crypted_password[crypted_idx ++] = plaintext_password[i];
		// ... and random bytes to pad.
		while (crypted_idx < crypted_password.length)
			crypted_password[crypted_idx++] = (byte)rng.nextInt();
		
		// Encrypt everything
		for (i = 0; i < Settings.password_length; i++)
			crypted_password[i] ^= encryption_bytes[i + offset];
		
		return crypted_password;
	}
	
	private static String decrypt_password(byte[] crypted_password, byte[] encryption_bytes, int offset)
	{
		int i, length;

		for (i = 0; i < Settings.password_length; i++)
			crypted_password[i] ^= encryption_bytes[i + offset];
		
		length = (int)crypted_password[0];
		return new String(crypted_password, 1, length);
	}
	
	private byte[] to_short(int i)
	{
		byte[] bytes = new byte[2];
		bytes[0] = (byte)((i & 0xff00) >> 8);
		bytes[1] = (byte)(i & 0xff);
		return bytes;
	}
	
	public boolean serialise(Context ctx, byte[] random_bytes)
	{
		byte encrypted_config;
		byte[] encrypted_password;
		
		encrypted_config = (byte)(((byte)config) ^ random_bytes[0]);
		encrypted_password = encrypt_password(random_bytes, 1);
				
		FileOutputStream nfcinfo;
		try {
			nfcinfo = ctx.openFileOutput(Settings.nfcinfo_filename_template + "_00.txt", Context.MODE_PRIVATE);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		try {
			nfcinfo.write(encrypted_config);
			nfcinfo.write(to_short(database.length()));
			nfcinfo.write(database.getBytes());
			nfcinfo.write(to_short(keyfile_filename.length()));
			nfcinfo.write(keyfile_filename.getBytes());
			nfcinfo.write(to_short(encrypted_password.length));
			nfcinfo.write(encrypted_password);
			nfcinfo.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
	public static DatabaseInfo deserialise(Context ctx, byte[] random_bytes, int offset)
	{
		int config = Settings.CONFIG_NOTHING;
		String database, keyfile, password;
		byte[] buffer = new byte[1024];
		byte[] encrypted_password = new byte[Settings.password_length];
		
		FileInputStream nfcinfo;
		
		try {
			nfcinfo = ctx.openFileInput(Settings.nfcinfo_filename_template + "_00.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		try {
			config = (((byte)nfcinfo.read()) ^ random_bytes[0 + offset]);
			database = read_string(nfcinfo, buffer);
			keyfile = read_string(nfcinfo, buffer);
			read_bytes(nfcinfo, encrypted_password);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		password = decrypt_password(encrypted_password, random_bytes, 1 + offset);
				
		return new DatabaseInfo(database, keyfile, password, config);
	}
	
	private static int read_short(FileInputStream fis, byte[] buffer) throws IOException
	{
		int i;
		
		fis.read(buffer, 0, 2);
		i = (int)(buffer[0] << 8);
		i |= (int)(buffer[1]);
		
		return i;
	}
	
	private static int read_bytes(FileInputStream fis, byte[] buffer) throws IOException
	{
		int length = read_short(fis, buffer);
		
		fis.read(buffer, 0, length);
		return length;		
	}
	
	private static String read_string(FileInputStream fis, byte[] buffer) throws IOException
	{
		int length = read_bytes(fis, buffer);
		return new String(buffer, 0, length);
	}
}
