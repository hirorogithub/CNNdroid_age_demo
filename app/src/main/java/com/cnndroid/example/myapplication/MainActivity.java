package com.cnndroid.example.myapplication;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import network.CNNdroid;

import messagepack.ParamUnpacker;
import network.CNNdroid;

import static android.graphics.Color.blue;
import static android.graphics.Color.green;
import static android.graphics.Color.red;


/*!!!!!!!!!!!!
* before running this app ,please ensure your MSG FILE and IMG FILE are in a RIGHT PATH!!
* !!!!!!!!!!!!!!!!
* */


public class MainActivity extends Activity {

    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_SELECT = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;

    private Button btnCamera;
    private Button btnSelect;
    private ImageView ivCaptured;
    private TextView text;
    private Uri fileUri;
    private ProgressDialog dialog;
    private Bitmap bmp;


    File sdcard = Environment.getExternalStorageDirectory();
    String modelDir =  "/sdcard/Data_CaffeNet/age_msg";
    String modelNetDef = modelDir + "/CaffeNet_def.txt";
    //String modelLabel = modelDir + "/labels.txt";
    String testPhoto = modelDir+ "/img/img (";

    private static String[] age_list={"(0, 2)","(4, 6)","(8, 12)","(15, 20)","(25, 32)","(38, 43)","(48, 53)","(60, 100)"};


    RenderScript myRenderScript;
    boolean condition = false;
    int imgSize = 0;
    int textSize = 20;
    CNNdroid myConv = null;
    String[] labels;
    float[][][] mean = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        text = (TextView) findViewById(R.id.textView);
        text.setText("Press \"Load Model\" to Load Network Parameters...");
        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnSelect = (Button) findViewById(R.id.btnSelect);



        myRenderScript = RenderScript.create(this);
        new prepareModel().execute(myRenderScript);

        /*
        try {
            // 1) Create a Renderscript object
            RenderScript myRenderScript = RenderScript.create(this);

            // 2) Construct a CNNdroid object
            //	  and provide NetFile location address.
            String NetFile = "/sdcard/Data_ImageNet2012/AlexNet_def.txt";
            CNNdroid myCNN = new CNNdroid(myRenderScript, NetFile);

            // 3) Prepare your input to the network.
            //		(The input can be single or batch of images.)
            float[][][]  inputSingle = new float[3][227][227];
            float[][][][] inputBatch = new float[16][3][227][227];

            // 4) Call the Compute function of the CNNdroid library
            //    and get the result of the CNN execution as an Object
            //	  when the computation is finished.
            Object output = myCNN.compute(inputSingle);

        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    private float[][][][] getPhoto(int val){

        float[][][][] inputBatch = new float[1][3][227][227];

        Bitmap bmp = BitmapFactory.decodeFile(testPhoto+val+").jpg");
        Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, 227, 227, false);

        for (int j = 0; j < 227; ++j)
            for (int k = 0; k < 227; ++k) {
                int color = bmp2.getPixel(j, k);
                inputBatch[0][0][k][j] = (float) (blue(color))-mean[0][j][k];
                inputBatch[0][1][k][j] = (float) (green(color))-mean[0][j][k];
                inputBatch[0][2][k][j] = (float) (red(color))-mean[0][j][k];
            }

        return inputBatch;
    }

    private class prepareModel extends AsyncTask<RenderScript, Void, int[]> {
        int cnt=20;// which mean I only read 20 img one time
        private long startTime;

        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected int[] doInBackground(RenderScript... params) {

            int[] result= new int[cnt] ;
            float [][] output = null;
            int maxi=0;
            try {
                myConv = new CNNdroid(myRenderScript,modelNetDef);
                ParamUnpacker pu = new ParamUnpacker();
                mean = (float[][][]) pu.unpackerFunction(modelDir+"/mean.msg", float[][][].class);

                // readLabels();
                float[][][][] tmp=null;
                for(int i=0;i<cnt;i++){
                    maxi=0;
                    tmp=getPhoto(i+1);
                    startTime = SystemClock.uptimeMillis();
                    output = (float[][]) myConv.compute(tmp);

                    for (int j=0;j<8;j++)
                        if(output[0][maxi]<output[0][j])
                            maxi=j;

                    result[i]=maxi;
                    Log.i(LOG_TAG, String.format("img%d Cal time: %d ms ", i,SystemClock.uptimeMillis() - startTime));
                    Log.i(LOG_TAG,accuracy(output[0],age_list,5));

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return (int[])result;
        }

        @Override
        protected void onPostExecute(int[] result) {

            String tmp="";
            for(int i=0;i<cnt;i++){
                tmp+=age_list[result[i]]+'\n';
                Log.i(LOG_TAG, String.format("%s Result:%s","img"+(i+1),String.valueOf(age_list[result[i]])));

            }

            text.setText(tmp);
            super.onPostExecute(result);
            //text.setText("\n" + accuracy(result[0], labels, 5));

        }


    }

    private String accuracy(float[] input_matrix, String[] labels, int topk) {
        String result = "";
        int[] max_num = {-1, -1, -1, -1, -1};
        float[] max = new float[topk];
        for (int k = 0; k < topk; ++k) {
            for (int i = 0; i < 8; ++i) {
                if (input_matrix[i] > max[k]) {
                    boolean newVal = true;
                    for (int j = 0; j < topk; ++j)
                        if (i == max_num[j])
                            newVal = false;
                    if (newVal) {
                        max[k] = input_matrix[i];
                        max_num[k] = i;
                    }
                }
            }
        }

        for (int i = 0; i < topk; i++)
            result += labels[max_num[i]] + " , P = " + max[i] * 100 + " %\n\n";
        return result;
    }


    /*  When you need a better UI please use follow function to complete your app
    *
    *   the function above just for testing how long it will take
    *   you can see the result and how long it take  by log
    *
    * */

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        //client.connect();
        //AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        //AppIndex.AppIndexApi.end(client, getIndexApiAction());
        //client.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_IMAGE_CAPTURE || requestCode == REQUEST_IMAGE_SELECT) && resultCode == RESULT_OK) {
            String imgPath;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgPath = fileUri.getPath();
            } else {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();
            }

            float[][][][] inputBatch = new float[1][3][227][227];
            ImageView img = (ImageView) findViewById(R.id.imageView);
            TextView text = (TextView) findViewById(R.id.textView);

            Bitmap bmp = BitmapFactory.decodeFile(imgPath);
            //Bitmap bmp = (Bitmap) data.getExtras().get("data");
            Bitmap bmp1 = Bitmap.createScaledBitmap(bmp, imgSize, imgSize, true);
            Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, 227, 227, false);
            img.setImageBitmap(bmp1);

            for (int j = 0; j < 227; ++j)
                for (int k = 0; k < 227; ++k) {
                    int color = bmp2.getPixel(j, k);
                    inputBatch[0][0][k][j] = (float) (blue(color)) - (float) 104.0079317889;
                    inputBatch[0][1][k][j] = (float) (green(color)) - (float) 116.66876761696767;
                    inputBatch[0][2][k][j] = (float) (red(color)) - (float) 122.6789143406786;
                }

            float[][] output = (float[][]) myConv.compute(inputBatch);
            text.setText("\n" + accuracy(output[0], labels, 5));
            text.setTextSize(textSize);

        } else {
            btnCamera.setEnabled(true);
            btnSelect.setEnabled(true);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void takePhoto(View view){
        initPrediction();
        fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(i, REQUEST_IMAGE_CAPTURE);

    }

    public void selectPhoto(View view) {
        initPrediction();
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, REQUEST_IMAGE_SELECT);
    }

    private void initPrediction() {
        btnCamera.setEnabled(false);
        btnSelect.setEnabled(false);
        //text.setText("");
    }
    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Caffe-Android-Demo");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else {
            return null;
        }

        return mediaFile;
    }

    /*private void readLabels() {
        labels = new String[1000];
        File f = new File(modelLabel);
        Scanner s = null;
        int iter = 0;

        try {
            s = new Scanner(f);
            while (s.hasNextLine()) {
                String str = s.nextLine();
                str = str.trim();
                str = str.substring(10);
                str = str.trim();
                labels[iter++] = str;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }*/

}
