package net.lardcave.keepassnfc;

public class Settings {
	public static final String nfc_mime_type = "application/x-keepassnfc-v1";
	public static final String nfcinfo_filename_template = "nfcinfo";
	public static final int password_length = 32; // including length byte
	public static final int index_length = 2; // Password filename number
	public static final int keyfile_length = 65; // including length byte
	public static final int random_bytes_length = password_length + keyfile_length;
	public static final int nfc_length = index_length + random_bytes_length;
}
