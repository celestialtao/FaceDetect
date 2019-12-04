package com.example.facedetect;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PICK_CODE =0X110 ;
    private String mCurrentPhotoStr;
    private ImageView mPhoto;
    private Button  mGetImage;
    private Button  mDetect;
    private TextView mTip;
    private View mWaiting;
    private Bitmap mPhotoImage;
    private Paint mPaint;

    private static final int  MSG_SUCCESS = 0x111;
    private static final int  MSG_ERROR = 0x112;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_SUCCESS:
                    mWaiting.setVisibility(View.GONE);
                    JSONObject res = (JSONObject) msg.obj;
                    prepareRsBitMap(res);

                    mPhoto.setImageBitmap(mPhotoImage); //显示脸部位置和信息的气泡

                    break;
                case MSG_ERROR:
                    mWaiting.setVisibility(View.GONE);
                    String errorMsg = (String) msg.obj;
                    if(TextUtils.isEmpty(errorMsg)){
                        mTip.setText("Error");
                    }else{
                        mTip.setText(errorMsg);
                    }
                    break;

            }
            super.handleMessage(msg);
        }
    };

    private void prepareRsBitMap(JSONObject res) {

        Bitmap bitmap = Bitmap.createBitmap(mPhotoImage.getWidth(), mPhotoImage.getHeight(), mPhotoImage.getConfig());
        Canvas canvas = new Canvas(bitmap);
//        绘制原图片，用于覆盖
        canvas.drawBitmap(mPhotoImage,0,0,null);

        try{
            JSONArray faces = res.getJSONArray("faces");
            int faceCount = faces.length();

            for (int i = 0; i < faceCount; i++){

                JSONObject face = faces.getJSONObject(i);
                JSONObject rectangle = face.getJSONObject("face_rectangle");

                float x = (float) rectangle.getInt("left");
                float y = (float) rectangle.getInt("top");
                float width =(float) rectangle.getInt("width");
                float height = (float) rectangle.getInt("height");

               /* x = x / 100 * bitmap.getWidth();
                y = y / 100 * bitmap.getWidth();
                width = width / 100 * bitmap.getWidth();
                height = height / 100 * bitmap.getWidth();*/

               mPaint.setColor(0xffffffff);
               mPaint.setStrokeWidth(3);
                //画box
                canvas.drawLine(x,y,x,y + height,mPaint); //竖线
                canvas.drawLine(x,y,x + width,y,mPaint);//横线
                canvas.drawLine(x + width,y,x + width,y + height,mPaint);
                canvas.drawLine(x + width,y + height,x,y + height,mPaint);

                //get age and gender
                int age = face.getJSONObject("attributes").getJSONObject("age").getInt("value");
                String gender = face.getJSONObject("attributes").getJSONObject("gender").getString("value");

                Bitmap ageBitmap = buildAgeBitmap(age, "Male".equals(gender));

                //缩放气泡
                int ageWidth = ageBitmap.getWidth();
                int ageHeight = ageBitmap.getHeight();

                if(bitmap.getWidth() < mPhoto.getWidth() && bitmap.getHeight() < mPhoto.getHeight()){

                    float ratio = Math.max(bitmap.getWidth() * 1.0f /
                            mPhoto.getWidth(),bitmap.getHeight() * 1.0f / mPhoto. getHeight());
                    ageBitmap = Bitmap.createScaledBitmap(ageBitmap, (int) (ageWidth * ratio),
                            (int) (ageHeight * ratio), false);
                }

                canvas.drawBitmap(ageBitmap,x, y  - ageBitmap.getHeight(),null);

                mPhotoImage = bitmap;


            }

        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    /**
     *  在TextView中绘制Bitmap
     * @param age
     * @param isMale
     * @return
     */
    private Bitmap buildAgeBitmap(int age, boolean isMale) {
        TextView tv = mWaiting.findViewById(R.id.age_and_gender);
        tv.setText(age + "");
        if(isMale){
             tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.male),null,null,null);
        }else{
            tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.female),null,null,null);
        }

        tv.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(tv.getDrawingCache());
        tv.destroyDrawingCache();

        return  bitmap;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initEvents();
        mPaint = new Paint();
    }

    private void initEvents() {
        mGetImage.setOnClickListener(this);
        mDetect.setOnClickListener(this);
    }

    private void initView() {
        mPhoto = findViewById(R.id.photo);
        mGetImage = findViewById(R.id.get_img);
        mDetect = findViewById(R.id.detect);
        mTip =  findViewById(R.id.tip);
        mWaiting = findViewById(R.id.waiting);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,Intent intent) {
        if(requestCode == PICK_CODE){
            if(intent != null){
                Uri uri = intent.getData();
                /*//返回游标
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                cursor.moveToFirst();
                //获取图片路径
                int idx = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                mCurrentPhotoStr = cursor.getString(idx);
                cursor.close();*/

                mCurrentPhotoStr = getFilePathFromURI(this, uri);

                //压缩图片，Face++的SDK对图片的尺寸要求不大于3M
                reSizePhoto();

                mPhoto.setImageBitmap(mPhotoImage);
                mTip.setText("Click Detect ==> ");
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void reSizePhoto() {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
//        获取图片的宽高
        BitmapFactory.decodeFile(mCurrentPhotoStr, options);
//       宽度和高度小于1024
        double ratio = Math.max(options.outWidth * 1.0d / 1024f, options.outHeight * 1.0d / 1024f);
//         设置压缩图片的比率
        options.inSampleSize =(int) Math.ceil(ratio);
        options.inJustDecodeBounds = false;
//        返回Bitmap
       mPhotoImage =  BitmapFactory.decodeFile(mCurrentPhotoStr, options);

    }


    private String getFilePathFromURI(Context context, Uri contentUri) {
        File rootDataDir = context.getFilesDir();
        String fileName = getFileName(contentUri);
        if (!TextUtils.isEmpty(fileName)) {
            File copyFile = new File(rootDataDir + File.separator + fileName);
            copyFile(context, contentUri, copyFile);
            return copyFile.getAbsolutePath();
        }
        return null;
    }


    public void copyFile(Context context, Uri srcUri, File dstFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null) return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            copyStream(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public int copyStream(InputStream input, OutputStream output) throws Exception, IOException {
        final int BUFFER_SIZE = 1024 * 2;
        byte[] buffer = new byte[BUFFER_SIZE];
        BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
        BufferedOutputStream out = new BufferedOutputStream(output, BUFFER_SIZE);
        int count = 0, n = 0;
        try {
            while ((n = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
                out.write(buffer, 0, n);
                count += n;
            }
            out.flush();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
            }
            try {
                in.close();
            } catch (IOException e) {
            }
        }
        return count;
    }

    public static String getFileName(Uri uri) {
        if (uri == null) return null;
        String fileName = null;
        String path = uri.getPath();
        int cut = path.lastIndexOf('/');
        if (cut != -1) {
            fileName = path.substring(cut + 1);
        }
        return fileName;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.get_img:

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent,PICK_CODE);

                break;

            case R.id.detect:

                mWaiting.setVisibility(View.VISIBLE);

                if (mCurrentPhotoStr != null && !mCurrentPhotoStr.trim().equals("")) {
                    //设置Bitmap
                    reSizePhoto();
                }else{
                    //直接使用介绍页面的图片
                    mPhotoImage = BitmapFactory.decodeResource(getResources(), R.drawable.t4);
                }

                FaceDetect.detect(mPhotoImage, new FaceDetect.CallBack() {
                    @Override
                    public void success(JSONObject result) {

                        Message msg = Message.obtain();
                        msg.what = MSG_SUCCESS;
                        msg.obj = result;
                        mHandler.sendMessage(msg);

                    }

                    public void error(Exception e) {

                        Message msg = Message.obtain();
                        msg.what = MSG_ERROR;
                        msg.obj = e;
                        mHandler.sendMessage(msg);

                    }
                });

                break;
        }
    }
}
