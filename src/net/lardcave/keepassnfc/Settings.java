package net.lardcave.keepassnfc;

public class Settings {
	public static final String nfc_mime_type = "application/x-keepassnfc-v1";
	public static final String nfcinfo_filename_template = "nfcinfo";
	public static final int password_length = 33; // including length byte
	public static final int index_length = 2; // Password filename number
	public static final int config_length = 1; // Config byte, currently set if ask for password
	public static final int random_bytes_length = password_length + config_length;
	public static final int nfc_length = index_length + random_bytes_length;
	
	public static final int CONFIG_NOTHING = 0;
	public static final int CONFIG_PASSWORD_ASK = 1;
}
