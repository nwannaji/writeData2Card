package com.example.writedata2card;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;
    private String[][] techListsArray;
    private ImageView photo;
    Button Save, capture;
    private MifareClassic mfc;
    byte[] imgbyte;
    String fname;
    private Tag tag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize NFC adapter and set up the foreground dispatch
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        intentFiltersArray = new IntentFilter[]{intentFilter};
        techListsArray = new String[][]{new String[]{MifareClassic.class.getName()}};

        // Get references to the views
        EditText et_firstname = findViewById(R.id.et_Firstname);
        Save = findViewById(R.id.btn_save);
        photo = findViewById(R.id.camera);
        capture = findViewById(R.id.btnSelectPhoto);

        Save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fname = et_firstname.getText().toString().trim();

                imgbyte = ImageUtils.getBytesFromImageView(photo);
                byte[] firstname = fname.getBytes();
                writeDataToCard(imgbyte,firstname);


            }
        });

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityIfNeeded(camera_intent, REQUEST_CAMERA_PERMISSION);
            }
        });

    }
    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    public void writeDataToCard(byte[] mPhotoData, byte [] firstname) {
        try {
            MifareClassic mfc = MifareClassic.get(tag);
            if (mfc != null) {
                byte[] key =  new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF};
                try {
                    mfc.connect();
                    // Get the number of sectors in the card.
                    int numSectors = mfc.getSectorCount();
                    boolean isAuthenticated;
                    for(int k = 1; k < numSectors; k++){
                        isAuthenticated =  mfc.authenticateSectorWithKeyA(k,key);
                        if (!isAuthenticated) {
                            Log.i("Authentication","Authentication Failed" + k);
                        }
                        else if (isAuthenticated) {
                            mfc.writeBlock(3,firstname);
                            imgbyte = mPhotoData;
                            List<byte[]> alist = new ArrayList<>(1);
                            alist.add(imgbyte);

                            // Select each sector and write the image data to each block
                            for (int i = 1; i <= 31; i++) {
                                int blockIndex = mfc.sectorToBlock(i);
                                for (int j = 0; j < 4; j++) {
                                    byte[] blockData = Arrays.copyOfRange(imgbyte, (i - 1) * 16 * 4 + j * 16, (i - 1) * 16 * 4 + (j + 1) * 16);
                                    mfc.writeBlock(blockIndex + j, blockData);
                                }
                            }
                            for (int i = 32; i <= 39; i++) {
                                int blockIndex = mfc.sectorToBlock(i);
                                for (int j = 0; j < 16; j++) {
                                    byte[] blockData = Arrays.copyOfRange(imgbyte, 1984 + (i - 32) * 16 * 16 + j * 16, 1984 + (i - 32) * 16 * 16 + (j + 1) * 16);
                                    mfc.writeBlock(blockIndex + j, blockData);
                                }
                            }
                            System.out.println("The size of the Image byte is: " + alist.size());
                            Toast.makeText(this, "Tag Written Successfully.", Toast.LENGTH_LONG).show();
                        }
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Error while writing to Mifare Classic card", e);
                } finally {
                    try {
                        // Disconnect from the card
                        mfc.close();
                    } catch (IOException e) {

                    }
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }

    }

                    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())){
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            MifareClassic mfc = MifareClassic.get(tag);
            if (tag != null && tag.getTechList()[0].equals(MifareClassic.class.getName())) {
                try {
                    mfc.connect();
                    boolean auth = mfc.authenticateSectorWithKeyA(1, MifareClassic.KEY_DEFAULT);
                    if (auth) {
                        Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        startActivityIfNeeded(camera_intent, REQUEST_CAMERA_PERMISSION);
                    }
                    mfc.close();
                } catch (IOException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }

            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Match the request 'pic id with requestCode
        if (requestCode == REQUEST_CAMERA_PERMISSION && resultCode == RESULT_OK) {
            // BitMap is data structure of image file which store the image in memory
            Bitmap photoBitmap = (Bitmap) data.getExtras().get("data");
            // Set the image in imageview for display
            photo.setImageBitmap(photoBitmap);
            RelativeLayout imageLayout = new RelativeLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(R.drawable.baseline_photo_camera_24);

            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
            imageLayout.addView(iv, lp);

        }
    }
}