package net.lardcave.keepassnfc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;

import com.ipaulpro.afilechooser.utils.FileUtils;

import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View.OnClickListener;


/* Probably want this to have foreground NFC-everything, so that people can scan a fob and then press the button?
 * Does that even work?
 */
public class WriteActivity extends Activity {

	private static final int PASSWORD_NO = 0;
	private static final int PASSWORD_ASK = 1;
	private static final int PASSWORD_YES = 2;
	private static final int KEYFILE_NO = 0;
	private static final int KEYFILE_YES = 1;
	private File keyfile;
	private byte[] random_bytes = new byte[Settings.random_bytes_length];
	NdefMessage nfc_payload;
	
	private int keyfile_option = KEYFILE_NO;
	private int password_option = PASSWORD_NO;
	
	@Override
	protected void onCreate(Bundle sis) {
		super.onCreate(sis);
		setContentView(R.layout.activity_write);
		
		if (sis != null) {
			password_option = sis.getInt("password_option");
			keyfile_option  = sis.getInt("keyfile_option");
			if (sis.getString("keyfile").compareTo("") != 0)
				keyfile = new File(sis.getString("keyfile"));
			else
				keyfile = null;
		}
		
		initialiseView();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle sis)
	{
	    super.onSaveInstanceState(sis);
	    if (keyfile == null)
	    	sis.putString("keyfile", "");
	    else
	    	sis.putString("keyfile", keyfile.getAbsolutePath());
	    sis.putInt("keyfile_option", keyfile_option);
	    sis.putInt("password_option", password_option);
	}
	
	private void initialiseView()
	{
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		
		updateRadioViews();
		updateNonRadioViews();
		
		RadioButton rb;
		
		rb = (RadioButton) findViewById(R.id.keyfile_no);
		rb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					keyfile_option = KEYFILE_NO;
				else
					keyfile_option = KEYFILE_YES;
				updateNonRadioViews();
			}});

		rb = (RadioButton) findViewById(R.id.password_yes);
		rb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					password_option = PASSWORD_YES;
				else
					password_option = PASSWORD_NO;
				updateNonRadioViews();
			}});
		
		Button b = (Button) findViewById(R.id.write_nfc);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View self) {
				create_random_bytes();
				if (encrypt_and_store())
				{
					nfc_enable();
					self.setEnabled(false);
				}
			}			
		});
		
		ImageButton ib = (ImageButton) findViewById(R.id.choose_keyfile);
		ib.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				keyfile_option = KEYFILE_YES;
				setRadio(R.id.keyfile_yes, true);
			    Intent target = FileUtils.createGetContentIntent();
			    Intent intent = Intent.createChooser(target, "Select a keyfile");
			    try {
			        startActivityForResult(intent, 0);
			    } catch (ActivityNotFoundException e) {
			    	e.printStackTrace();
			    }
			}
			
		});
	}

	// Stuff came back from file chooser
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    switch (requestCode) {
	    case 0:  
	        if (resultCode == RESULT_OK) {  
	            // The URI of the selected file 
	            final Uri uri = data.getData();
	            // Create a File from this Uri
	            keyfile = FileUtils.getFile(uri);
	            updateNonRadioViews();
	        }
	        break;
	    }
	}

	
	private void create_random_bytes()
	{
		SecureRandom rng = new SecureRandom();		
		rng.nextBytes(random_bytes);

		byte[] nfcinfo_index = new byte[Settings.index_length];
		nfcinfo_index[0] = 0;
		nfcinfo_index[1] = 0;
		
		assert(Settings.index_length + Settings.password_length + Settings.keyfile_length == Settings.nfc_length);
		byte[] nfc_all = new byte[Settings.nfc_length];
		System.arraycopy(nfcinfo_index, 0, nfc_all, 0, Settings.index_length);
		System.arraycopy(random_bytes, 0, nfc_all, Settings.index_length, Settings.random_bytes_length);
		
		// Create the NFC version of this data		
		NdefRecord ndef_records = NdefRecord.createMime(Settings.nfc_mime_type, nfc_all);
		nfc_payload = new NdefMessage(ndef_records);
	}
	
	private byte[] read_key_file()
	{
		FileInputStream keyfile_stream;
		try {
			keyfile_stream = openFileInput(keyfile.getAbsolutePath());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		byte[] data = new byte[(int)keyfile.length()];
		try {
			keyfile_stream.read(data);
			keyfile_stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return data;
	}
	
	private boolean encrypt_and_store()
	{	
		SecureRandom rng = new SecureRandom();		

		// Read the key file, if we have one.
		byte[] keyfile_bytes = null;
		
		if (keyfile_option == KEYFILE_YES)
		{
			keyfile_bytes = read_key_file();
			if (keyfile_bytes == null) {
				Toast.makeText(getApplicationContext(), "Couldn't read key file", Toast.LENGTH_SHORT).show();
				return false;
			}
		}
		
		// Grab the password.
		byte[] password_bytes = null;
		
		if (password_option == PASSWORD_YES)
		{
			EditText et_password = (EditText) findViewById(R.id.password);
			password_bytes = et_password.getText().toString().getBytes();
		}
		
		// Create the unencrypted version, consisting of:
		// 2 bytes: database filename length (currently 0 = unused)
		// n bytes: database filename (currently 0 bytes)
		// 1 byte : config (currently 0 = don't ask for password; 1 = ask for password)
		// 1 byte : password length, or 0 for no password (to be encrypted)
		// n bytes: password (to be encrypted)
		// 1 byte : keyfile length, or 0 for no keyfile (to be encrypted)
		// n bytes: keyfile (to be encrypted)
		byte[] encrypted_all = new byte[2 + 0 + 1 + Settings.password_length + Settings.keyfile_length];
		int pos, i, size;
		
		pos = 0;
		
		// Database filename length
		encrypted_all[pos++] = 0;
		encrypted_all[pos++] = 0;
		// No database filename.
		// Config byte
		if (password_option == PASSWORD_ASK)
			encrypted_all[pos++] = 1;
		else
			encrypted_all[pos++] = 0;

		// Password
		encrypted_all[pos++] = (byte)password_bytes.length;
		for (i = 0; i < password_bytes.length; i++) {
			encrypted_all[pos++] = password_bytes[i];
		}
		// Pad out the password with random data. NB Settings.password_length also includes the length byte.
		size = (Settings.password_length - 1) - password_bytes.length;
		if (size > 0) { // TODO: Detect & reject too-long passwords
			for (i = 0; i < size; i++)
				encrypted_all[pos++] = (byte)(rng.nextInt() & 0xff);
		}
		
		// Keyfile
		encrypted_all[pos++] = (byte)keyfile_bytes.length;
		for (i = 0; i < keyfile_bytes.length; i++) {
			encrypted_all[pos++] = keyfile_bytes[i];
		}
		// Do the same padding with keyfile data.
		size = (Settings.keyfile_length - 1) - keyfile_bytes.length;
		if (size > 0) {
			for (i = 0; i < size; i++)
				encrypted_all[pos++] = (byte)(rng.nextInt() & 0xff);				
		}
		
		assert (pos == encrypted_all.length);
		assert (encrypted_all.length - 2 == random_bytes.length);
		
		// Encrypt everything
		for (i = 2; i < encrypted_all.length; i++)
			encrypted_all[i] ^= random_bytes[i - 2];
		
		// Write it to permanent storage
		
		return true;
	}
	
	private void nfc_enable()
	{
		// Register for any NFC event (only while we're in the foreground)

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        PendingIntent pending_intent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        
        adapter.enableForegroundDispatch(this, pending_intent, null, null);
	}
	
	private void nfc_disable()
	{
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        
        adapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		nfc_disable();
	}
	
	private void updateRadioViews()
	{
		setRadio(R.id.keyfile_no, keyfile_option == KEYFILE_NO);
		setRadio(R.id.keyfile_yes, keyfile_option == KEYFILE_YES);
		setRadio(R.id.password_no, password_option == PASSWORD_NO);
		setRadio(R.id.password_ask, password_option == PASSWORD_ASK);
		setRadio(R.id.password_yes, password_option == PASSWORD_YES);		
	}
	
	private void updateNonRadioViews()
	{
		EditText et_password = (EditText) findViewById(R.id.password);
		et_password.setEnabled(password_option == PASSWORD_YES);		
		
		TextView tv_keyfile = (TextView) findViewById(R.id.keyfile_name);
		tv_keyfile.setEnabled(keyfile_option == KEYFILE_YES);
		if (keyfile != null)
			tv_keyfile.setText(keyfile.getAbsolutePath());
		else
			tv_keyfile.setText("...");
	}
	
	private void setRadio(int id, boolean checked)
	{
		RadioButton rb = (RadioButton) findViewById(id);
		rb.setChecked(checked);
	}

/*	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.write, menu);
		return true;
	}
*/
	@Override
	public void onNewIntent(Intent intent)
	{
		String action = intent.getAction();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) 
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
			boolean success = false;
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

			// Write the payload to the tag.
			Ndef ndef = Ndef.get(tag);
			try {
				ndef.connect();
				ndef.writeNdefMessage(nfc_payload);
				ndef.close();
				success = true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (FormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// Re-enable NFC writing.
			Button nfc_write = (Button) findViewById(R.id.write_nfc);
			nfc_write.setEnabled(true);
			
			if (success) {
				// Job well done! Let's have some toast.
				Toast.makeText(getApplicationContext(), "Tag written successfully!", Toast.LENGTH_SHORT).show();
			} else {
				// can't think of a good toast analogy for fail
				Toast.makeText(getApplicationContext(), "Couldn't write tag. :(", Toast.LENGTH_SHORT).show();
			}
		}
	}

}
