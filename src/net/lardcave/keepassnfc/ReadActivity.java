package net.lardcave.keepassnfc;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;

public class ReadActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		byte[] payload = null;
		
		Intent intent = getIntent();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
	        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
	        if (rawMsgs != null) {
	            NdefMessage [] msgs = new NdefMessage[rawMsgs.length];
	            for (int j = 0; j < rawMsgs.length; j++) {
	                msgs[j] = (NdefMessage) rawMsgs[j];
	                NdefRecord record = msgs[j].getRecords()[0];
	                if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA)
	                {
	                	String mimetype = record.toMimeType();
	                	if (mimetype.equals(Settings.nfc_mime_type)) {
		                	payload = record.getPayload();
	                	}
	                }
	            }
	        }
		}
		
		if (payload != null) {
			load_from_nfc(payload);
		}
	}
	
	private boolean load_from_nfc(byte[] payload)
	{
		// Ignore first two bytes of payload (it's a filename index which is unused)
		DatabaseInfo dbinfo = DatabaseInfo.deserialise(this, payload, 2);
		
		return startKeepassActivity(dbinfo);
	}
	
	private boolean startKeepassActivity(DatabaseInfo dbinfo)
	{
		Intent intent = new Intent();
		
		intent.setComponent(new ComponentName("com.android.keepass", "com.keepassdroid.PasswordActivity"));
		intent.putExtra("fileName", dbinfo.database);
		intent.putExtra("keyFile", dbinfo.keyfile_filename);
		intent.putExtra("password", dbinfo.password);
		intent.putExtra("launchImmediately", dbinfo.config != Settings.CONFIG_PASSWORD_ASK);
		startActivity(intent);
		return true;
	}

}
