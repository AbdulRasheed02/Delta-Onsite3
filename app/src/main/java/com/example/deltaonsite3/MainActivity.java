 package com.example.deltaonsite3;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;


import android.Manifest;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

 public class MainActivity extends AppCompatActivity {

    Button btn_selectImage,btn_takePicture;
    private static final int REQUEST_PICK_IMAGE=12345;
     private static final int REQUEST_PERMISSIONS=1234;
     private static final String[] PERMISSIONS={
             Manifest.permission.READ_EXTERNAL_STORAGE,
             Manifest.permission.WRITE_EXTERNAL_STORAGE
     };
     private static final String appID="photoEditor";
     private static final int REQUEST_IMAGE_CAPTURE=1012;
     private Uri ImageUri;
     private boolean editMode;
     private Bitmap bitmap;
     private int width=0,height=0;
     private static final int MAX_PIXEL_COUNT=2048;
     private ImageView imageView;
     private int[] pixels;
     private int pixelCount=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_selectImage=findViewById(R.id.selectImage);
        btn_takePicture=findViewById(R.id.takePicture);
        imageView=findViewById(R.id.imageView);

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
            StrictMode.VmPolicy.Builder builder=new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        btn_selectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                final Intent pickIntent=new Intent(Intent.ACTION_PICK);
                pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
                final Intent chooserIntent=Intent.createChooser(intent,"Select Image");
                startActivityForResult(chooserIntent,REQUEST_PICK_IMAGE);
            }
        });

        btn_takePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent takePictureIntent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    final File photoFile=createImageFile();
                    ImageUri=Uri.fromFile(photoFile);
                    final SharedPreferences myPrefs=getSharedPreferences(appID,0);
                    myPrefs.edit().putString("path",photoFile.getAbsolutePath()).apply();
                }

            }
        });

        final Button greyScale=findViewById(R.id.greyScale);
        greyScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    public void run() {
                        for(int i=0;i<pixelCount;i++){
                            pixels[i]/=2;
                        }
                        bitmap.setPixels(pixels,0,width,0,0,width,height);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }.start();
            }
        });

    }

    private File createImageFile(){
        final String timeStamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        final String imageFileName="JPEG_"+timeStamp+".jpg";
        final File storageDir= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(storageDir+imageFileName);
    }



    private static final int PERMISSIONS_COUNT=2;

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean notPermission(){
        for(int i=0;i<PERMISSIONS_COUNT;i++){
            if(checkSelfPermission(PERMISSIONS[i])!= PackageManager.PERMISSION_GRANTED){
                return true;
            }
        }
        return false;
    }

     @RequiresApi(api = Build.VERSION_CODES.M)
     @Override
     protected void onResume() {
         super.onResume();
         if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && notPermission()){
             requestPermissions(PERMISSIONS,REQUEST_PERMISSIONS);
         }
     }

     @RequiresApi(api = Build.VERSION_CODES.M)
     @Override
     public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
         super.onRequestPermissionsResult(requestCode, permissions, grantResults);
         if(notPermission()){
             ((ActivityManager)this.getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
             recreate();
         }
     }

     @Override
     protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
         super.onActivityResult(requestCode, resultCode, data);
         if(resultCode!=RESULT_OK){
             return;
         }
         if(requestCode==REQUEST_IMAGE_CAPTURE){
             if(ImageUri==null){
                 final SharedPreferences p=getSharedPreferences(appID,0);
                 final String path=p.getString("path","");
                 if(path.length()<1){
                     recreate();
                     return;
                 }
                 ImageUri=Uri.parse("file://"+path);
             }
             sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,ImageUri));
         }
         else if(requestCode==REQUEST_PICK_IMAGE){
             ImageUri=data.getData();
         }

         final ProgressDialog dialog= ProgressDialog.show(MainActivity.this,"Loading","Please Wait",true);

         editMode=true;

         findViewById(R.id.homeScreen).setVisibility(View.GONE);
         findViewById(R.id.editScreen).setVisibility(View.VISIBLE);

         new Thread(){
             public void run(){
                 bitmap=null;
                 final BitmapFactory.Options bmpOptions=new BitmapFactory.Options();
                 bmpOptions.inBitmap=bitmap;
                 bmpOptions.inJustDecodeBounds=true;
                 try(InputStream input=getContentResolver().openInputStream(ImageUri)){
                     bitmap=BitmapFactory.decodeStream(input,null,bmpOptions);
                 }catch(IOException e){
                     e.printStackTrace();
                 }
                 bmpOptions.inJustDecodeBounds=false;
                 width=bmpOptions.outWidth;
                 height=bmpOptions.outHeight;
                 int resizeScale=1;
                 if(width>MAX_PIXEL_COUNT){
                     resizeScale=height/MAX_PIXEL_COUNT;
                 }
                 else if(height>MAX_PIXEL_COUNT){
                     resizeScale=width/MAX_PIXEL_COUNT;
                 }
                 if(width/resizeScale>MAX_PIXEL_COUNT|| height/resizeScale>MAX_PIXEL_COUNT) {
                     resizeScale++;
                 }
                 bmpOptions.inSampleSize=resizeScale;
                 InputStream input=null;
                 try{
                     input=getContentResolver().openInputStream(ImageUri);
                 } catch (FileNotFoundException e) {
                     e.printStackTrace();
                     recreate();
                     return;
                 }
                 bitmap=BitmapFactory.decodeStream(input,null,bmpOptions);
                 runOnUiThread(new Runnable() {
                     @Override
                     public void run() {
                         imageView.setImageBitmap(bitmap);
                         dialog.cancel();
                     }
                 });
                 width=bitmap.getWidth();
                 height=bitmap.getHeight();
                 bitmap=bitmap.copy(Bitmap.Config.ARGB_8888,true);

                 pixelCount=width*height;
                 pixels=new int[pixelCount];
                 bitmap.getPixels(pixels,0,width,0,0,width,height);

             }
         }.start();
     }
 }