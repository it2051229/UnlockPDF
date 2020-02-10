package it2051229.unlockpdf;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;

import java.io.File;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // Where to save passwords.
    private static final String PASSWORDS_FILENAME = "passwords.dat";

    // Directory filename where to save the unlocked PDF.
    private static final String OUTPUT_DIRECTORY_NAME = "UnlockPDF";

    // Name of the unlocked PDF file
    private static final String OUTPUT_FILENAME = "Unlocked PDF";

     // Directory object where to save the unlocked PDF
    private static final File OUTPUT_DIRECTORY = new File(Environment.getExternalStorageDirectory() + "/" + OUTPUT_DIRECTORY_NAME);

    // File object that will hold the unlocked PDF
    private static final File OUTPUT_FILE = new File(Environment.getExternalStorageDirectory() + "/" + OUTPUT_DIRECTORY_NAME + "/" + OUTPUT_FILENAME);

    // Data structure where to save our passwords
    private Set<String> passwords = new HashSet<>();

    // Entry point of our program. We expect the user to first select a PDF file in which
    // our app is going to be an option. If the user chooses our app as the option to open the
    // PDF then we have to attempt to unlock it. If attempts failed, we ask the user for a password
    // and attempt to unlock it again. If unlocked, we save the new password.
    //
    // If unlock is successful, then we programmatically open the file and it's up to android
    // to show all PDF viewer apps to open the unlocked PDF.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request for permissions that aren't enabled
        String[] permissions = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // If one of the permissions aren't granted, then request for it
                ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
                return;
            }
        }

        // Create the output directory if it doesn't exist yet
        OUTPUT_DIRECTORY.mkdir();

        // Load all saved passwords
        try {
            ObjectInputStream ois = new ObjectInputStream(openFileInput(PASSWORDS_FILENAME));
            passwords = (Set<String>) ois.readObject();
            ois.close();
        } catch (Exception e) {
            // Exception is thrown is if the passwords file is corrupt or does not exist yet.
            // If that's the case then we make a new one.
        }

        // Check if our app is opened by a user by first selecting a PDF file and chosen
        // our app to open it
        final Intent intent = getIntent();

        if (intent != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            try {
                // Attempt to open the PDF using the stored passwords
                if (unlockPdf(intent.getData())) {
                    openUnlockedPdf();
                    return;
                }

                // If none of the passwords unlocked the PDF then ask the user for the password
                // in a dialog window
                final EditText passwordEditText = new EditText(this);
                passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);

                new AlertDialog.Builder(this)
                    .setMessage("PDF Password: ")
                    .setView(passwordEditText)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String password = passwordEditText.getText().toString();

                            // Attempt to open the PDF using the given password
                            if(unlockPdf(intent.getData(), password)) {
                                passwords.add(password);
                                savePasswords();

                                // Open the unlocked PDF
                                openUnlockedPdf();
                            } else {
                                Toast.makeText(MainActivity.this, "Oh snap! The password is invalid or this PDF is not locked.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
            } catch(Exception e) {
                Log.e("onCreate", e.getMessage());
            }
        }
    }

    // Handle the response from permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode == 1)  {
            if(grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                // Stop application if the permission has not been app
                // removed by the user
                Toast.makeText(this, "The application cannot run if you do not allow read/write storage permission.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // Delete all saved passwords
    public void clearSavedPasswords(View view) {
        // Confirm the user for deletion
        new AlertDialog.Builder(this)
            .setMessage("Are you sure you want to clear all saved passwords?")
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    passwords.clear();
                    savePasswords();
                    Toast.makeText(MainActivity.this, "All saved passwords has been removed.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("No", null)
            .create()
            .show();
    }

    // Attempt the unlock the PDF using all passwords available
    private boolean unlockPdf(Uri pdfUri) {
        // Try to unlock it first without a password
        try {
            PdfReader reader = new PdfReader(getContentResolver().openInputStream(pdfUri));
            PdfDocument pdfDoc = new PdfDocument(reader, new PdfWriter(OUTPUT_FILE.getAbsolutePath()));
            pdfDoc.close();

            return true;
        } catch(Exception e) {
        }

        // If it really has a password then unlock it
        for(String password : passwords) {
            if(unlockPdf(pdfUri, password))
                return true;
        }

        return false;
    }

    // Attempt to unlock PDF using the given password
    private boolean unlockPdf(Uri pdfUri, String password) {
        try {
            PdfReader reader = new PdfReader(getContentResolver().openInputStream(pdfUri), new ReaderProperties().setPassword(password.getBytes()));
            PdfDocument pdfDoc = new PdfDocument(reader, new PdfWriter(OUTPUT_FILE.getAbsolutePath()));
            reader.computeUserPassword();
            pdfDoc.close();

            return true;
        } catch(Exception e) {
            Log.e("unlockPdf", e.getMessage());
        }

        return false;
    }

    // Call Android's OS to open the unlocked PDF file
    private void openUnlockedPdf() {
        Toast.makeText(this, "Hurray! PDF unlocked! Now please choose a PDF Viewer for opening the unlocked document.", Toast.LENGTH_LONG).show();
        Uri pdfFilePath = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", OUTPUT_FILE);
        Intent pdfIntent = new Intent(Intent.ACTION_VIEW);
        pdfIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        pdfIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pdfIntent.setDataAndType(pdfFilePath, "application/pdf");

        // Remove this app as a suggestion for opening PDF files
        final PackageManager pm = getApplicationContext().getPackageManager();
        final ComponentName compName = new ComponentName(getPackageName(), getPackageName() + ".MainActivity");
        pm.setComponentEnabledSetting(
            compName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);

        try {
            startActivity(pdfIntent);
        } catch(Exception e) {
            Log.e("openUnlockedPdf", e.getMessage());
        }

        // Pause for a second giving time for android to open the intent for options
        // then restore the app as an option for opening PDF again
        new Thread(new Runnable(){
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch(Exception e) {
                }

                // Restore this app as a suggestion for opening PDF files
                pm.setComponentEnabledSetting(
                        compName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }).start();

    }

    // Save all passwords to file
    private void savePasswords() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(openFileOutput(PASSWORDS_FILENAME, Context.MODE_PRIVATE));
            oos.writeObject(passwords);
            oos.close();
        } catch(Exception e) {
            Log.e("savePasswords", e.getMessage());
        }
    }
}
