package net.sheetmuse.sheetmuse;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.ResultCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import butterknife.OnClick;

import static android.app.ProgressDialog.STYLE_HORIZONTAL;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class MainActivity extends AppCompatActivity {

    // Requesting permission to record audio and store files
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;

        }
        if (!permissionToRecordAccepted) finish();

    }


    // buttons and stuff
    private Button mRecordBtn;
    private TextView mRecordLabel;
    private MediaRecorder mRecorder;

    public String mFilePath = null;
    public String mAudioFileName = null;
    public String mMidiFileName = null;
    public String mDatePrefix = null;

    private static final String LOG_TAG = "Record_log";

    private TextView mDownloadLabel;
    public ProgressDialog mDownloadProgress;


    private StorageReference mStorage;
    private ProgressDialog mProgress;
    private UploadTask uploadTask;

    // Create a storage reference from our app
    private StorageReference storageRef;

    public String Md5Hash = "HELLO";


    private static final String TAG = "MyFirebaseIIDService";


    private static final int RC_SIGN_IN = 123;
    Button mSignoutButton;

    private TextView mDisplayUser;
    public String userName = "NOTHING";

    // this gets a ref to the root of the .json rt database tree
    FirebaseDatabase database = FirebaseDatabase.getInstance();

    DatabaseReference mRootRef = database.getReference();

    public DatabaseReference mUserRef;
    public DatabaseReference mMidiFile;


    private void setup_auth_login() {
        // get UI elements
        mSignoutButton = (Button) findViewById(R.id.signoutbtn);
        mSignoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
                new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
                new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build(),
                new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER).build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .setTheme(R.style.splashscreenTheme)
                        .setIsSmartLockEnabled(true)
                        .build(),
                RC_SIGN_IN);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);


        setup_auth_login();
        setup_audio_recorder();


    }

    private void setup_audio_recorder() {

        // download stuff
        mRecordBtn = (Button) findViewById(R.id.RecordBtn);
        mRecordLabel = (TextView) findViewById(R.id.RecordLabel);
        mProgress = new ProgressDialog(this);
        mProgress.setProgressStyle(STYLE_HORIZONTAL);
        mDownloadProgress = new ProgressDialog(this);
        mDownloadProgress.setProgressStyle(STYLE_HORIZONTAL);

        // upload
        mStorage = FirebaseStorage.getInstance().getReference();

        // download

        mDownloadLabel = (TextView) findViewById(R.id.DownloadLabel);


        mRecordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    // user has pressed down on the button
                    startRecording();
                    mRecordLabel.setText("Recording Started ...");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    // user has let go of the button
                    stopRecording();
                    mRecordLabel.setText("Recording Stopped ...");
                }
                return false;
            }
        });

    }


    private void updateDataBase() {

        mUserRef = mRootRef.child(mDatePrefix).getRef();

        DatabaseReference mEmailRef = mUserRef.child("username").getRef();
        mEmailRef.setValue(userName);

        DatabaseReference mAudioFile = mUserRef.child("audiofile").getRef();
        mAudioFile.setValue(mAudioFileName);


    }

    private void checkDataBaseForMidi() {
        mUserRef = mRootRef.child(mDatePrefix).getRef();

        mUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.hasChild("midifile")){
                    mDownloadProgress.setProgress(50);
                    mDownloadProgress.show();
                    downloadMidi();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }


    private void startRecording() {


        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // change this for file


        // Set location here
        mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();

        // get date/time
        mDatePrefix = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss").format(Calendar.getInstance().getTime());
        mAudioFileName = mDatePrefix + ".aac";
        mMidiFileName = mDatePrefix + ".mid";

        mFilePath += "/" + mAudioFileName;
        mRecorder.setOutputFile(mFilePath);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);


        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        mProgress.dismiss();
        mDownloadProgress.setMessage("Transcribing ...");
        mDownloadProgress.show();
        uploadAudio();

        updateDataBase();

        checkDataBaseForMidi();


    }

    private void uploadAudio() {
//
        mProgress.setMessage("Uploading Audio ...");
        mProgress.show();

        Uri file = Uri.fromFile(new File(mFilePath)); // get full local device file path
        StorageReference recordingRef = mStorage.child(mAudioFileName);
        uploadTask = recordingRef.putFile(file);

        // Observe state change events such as progress, pause, and resume
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                System.out.println("Upload is " + progress + "% done");
                int currentprogress = (int) progress;
                mProgress.setProgress(currentprogress);
            }
        }).addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                System.out.println("Upload is paused");
            }
        });


        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                mProgress.dismiss();
                mRecordLabel.setText(mAudioFileName);

                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                Uri downloadUrl = taskSnapshot.getDownloadUrl();
            }
        });

    }

    private void downloadMidi() {

        mDownloadProgress.setMessage("Downloading MIDI ...");
        mDownloadProgress.setProgress(80);
        mDownloadProgress.show();

//        String mMidiFileName = Md5Hash + ".mid";

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReferenceFromUrl("gs://sheetmuse.appspot.com");
        StorageReference midiRef = storageRef.child(mAudioFileName);
//        mMidiFileName
        midiRef.getDownloadUrl();


        // set up file directory on local device for downloading midi
        File rootPath = new File(Environment.getExternalStorageDirectory(), mMidiFileName);
        if (!rootPath.exists()) {
            rootPath.mkdirs();
        }
        final File localFile = new File(rootPath, mMidiFileName);


        // try and download midi
        midiRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {

                mDownloadProgress.dismiss();
                mDownloadLabel.setText("Downloading Finished");
                Log.e("firebase ", ";local tem file created  created " + localFile.toString());
                // display new screen
                startActivity(new Intent(getApplicationContext(), MIDI.class));


            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                int errorCode = ((StorageException) exception).getErrorCode();
                String errorMessage = exception.getMessage();

                // test the errorCode and errorMessage, and handle accordingly
                mDownloadProgress.dismiss();
                mDownloadLabel.setText(errorMessage);
                Log.e("firebase ", ";local tem file not created  created " + exception.toString());
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == ResultCodes.OK) {
                // Successfully signed in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                mDisplayUser = (TextView) findViewById(R.id.DisplayUser);
                String email = user.getEmail();
                Uri photoUrl = user.getPhotoUrl();
                userName = email;
                mDisplayUser.setText(email);

            }
        }
    }

    @OnClick(R.id.signoutbtn)
    public void signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
//                            startActivity(new Intent(MainActivity.this, SecondActivity.class));
//                            finish();
                            setup_auth_login();
                        } else {
//                            showSnackbar(R.string.sign_out_failed);
                        }
                    }
                });
        AuthUI.getInstance()
                .delete(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Deletion succeeded
                        } else {
                            // Deletion failed
                        }
                    }
                });


    }

}
