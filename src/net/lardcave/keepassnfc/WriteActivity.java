package net.lardcave.keepassnfc;

import java.io.File;
import java.security.SecureRandom;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.ipaulpro.afilechooser.utils.FileUtils;


/* Probably want this to have foreground NFC-everything, so that people can scan a fob and then press the button?
 * Does that even work?
 */
public class WriteActivity extends Activity {

	private static final int PASSWORD_NO = 0;
	private static final int PASSWORD_ASK = 1;
	private static final int PASSWORD_YES = 2;
	private static final int KEYFILE_NO = 0;
	private static final int KEYFILE_YES = 1;
	private static final int REQUEST_KEYFILE = 0;
	private static final int REQUEST_DATABASE = 1;
    private static final int REQUEST_NFC_WRITE = 2;
	private File keyfile = null;
	private File database = null;
	private byte[] random_bytes = new byte[Settings.random_bytes_length];
	public static NdefMessage nfc_payload;
	
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
                self.setEnabled(false);
                Intent intent = new Intent(getApplicationContext(), WriteNFCActivity.class);
                startActivityForResult(intent, REQUEST_NFC_WRITE);
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
			        startActivityForResult(intent, REQUEST_KEYFILE);
			    } catch (ActivityNotFoundException e) {
			    	e.printStackTrace();
			    }
			}
			
		});
		
		ib = (ImageButton) findViewById(R.id.choose_database);
		ib.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
			    Intent target = FileUtils.createGetContentIntent();
			    Intent intent = Intent.createChooser(target, "Select a database");
			    try {
			        startActivityForResult(intent, REQUEST_DATABASE);
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
	    case REQUEST_KEYFILE:  
	        if (resultCode == RESULT_OK) {  
	            // The URI of the selected file 
	            final Uri uri = data.getData();
	            // Create a File from this Uri
	            keyfile = FileUtils.getFile(this, uri);
	            updateNonRadioViews();
	        }
	        break;
	    case REQUEST_DATABASE:
	    	if (resultCode == RESULT_OK) {
	    		final Uri uri = data.getData();
	    		database = FileUtils.getFile(this, uri);
	    		updateNonRadioViews();
	    	}
	    	break;
        case REQUEST_NFC_WRITE:
            // Re-enable NFC writing.
            Button nfc_write = (Button) findViewById(R.id.write_nfc);
            nfc_write.setEnabled(true);

            if (resultCode == 1) {
                if (encrypt_and_store()) {
                    // Job well done! Let's have some toast.
                    Toast.makeText(getApplicationContext(), "Tag written successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error writing to application database!", Toast.LENGTH_SHORT).show();
                }
            } else {
                // can't think of a good toast analogy for fail
                Toast.makeText(getApplicationContext(), "Couldn't write tag. :(", Toast.LENGTH_SHORT).show();
            }
        }
	}

	
	private void create_random_bytes()
	{
		SecureRandom rng = new SecureRandom();		
		rng.nextBytes(random_bytes);

		byte[] nfcinfo_index = new byte[Settings.index_length];
		nfcinfo_index[0] = '0';
		nfcinfo_index[1] = '0';
		
		assert(Settings.index_length + Settings.password_length == Settings.nfc_length);
		byte[] nfc_all = new byte[Settings.nfc_length];
		System.arraycopy(nfcinfo_index, 0, nfc_all, 0, Settings.index_length);
		System.arraycopy(random_bytes, 0, nfc_all, Settings.index_length, Settings.random_bytes_length);
		
		// Create the NFC version of this data		
		NdefRecord ndef_records = NdefRecord.createMime(Settings.nfc_mime_type, nfc_all);
		nfc_payload = new NdefMessage(ndef_records);
	}
	
	private boolean encrypt_and_store()
	{	
		DatabaseInfo dbinfo;
		int config;
		String keyfile_filename;
		String password;
		
		if (database == null) {
			Toast.makeText(getApplicationContext(), "Please select a database first", Toast.LENGTH_SHORT).show();
			return false;
		}
		
		if (password_option == PASSWORD_ASK)
			config = Settings.CONFIG_PASSWORD_ASK;
		else
			config = Settings.CONFIG_NOTHING;
		
		// Policy: no password is stored as null password (bit silly?)
		if (password_option == PASSWORD_NO)
			password = "";
		else {
			EditText et_password = (EditText) findViewById(R.id.password);
			password = et_password.getText().toString();
		}
		
		// Policy: no keyfile is stored as empty filename.		
		if (keyfile_option == KEYFILE_NO)
			keyfile_filename = "";
		else {
			keyfile_filename = keyfile.getAbsolutePath();
		}
				
		dbinfo = new DatabaseInfo(database.getAbsolutePath(), keyfile_filename, password, config);
		
		return dbinfo.serialise(this, random_bytes);
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
		
		TextView tv_database = (TextView) findViewById(R.id.database_name);
		if (database != null)
			tv_database.setText(database.getAbsolutePath());
		else
			tv_database.setText("...");
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

}
