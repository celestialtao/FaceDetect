package com.example.facedetect;

import android.graphics.Bitmap;
import android.util.Log;


import com.megvii.cloud.http.CommonOperate;
import com.megvii.cloud.http.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class FaceDetect {

    public interface CallBack{

        void success(JSONObject result);

        void error(Exception e);
    }

    public static void detect(final Bitmap bmp, final CallBack callBack) {

        new Thread(new Runnable() {
            @Override
            public void run() {
               try{

                   //request
                   CommonOperate commonOperate = new CommonOperate(Constant.KEY, Constant.SECREAT, false);
                   Bitmap bmSmall = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight());
                   ByteArrayOutputStream stream = new ByteArrayOutputStream();
                   bmSmall.compress(Bitmap.CompressFormat.PNG,100,stream);

                   byte[] imageBytes = stream.toByteArray();

                   Response response= commonOperate.detectByte(imageBytes, 0, Constant.FACE_ATTRIBUTTE);   //返回一个jason数据
                   JSONObject inf = getJson(response);
                   /*PostParameters params =new PostParameters();
                   params.setImg(arrays);
                   //返回最终处理数据
                   JSONObject jsonObject = request.detectionDetect(params);*/

                   //Log
                   if(callBack != null ){
                       callBack.success(inf);
                   }


               }catch (Exception e){
                   e.printStackTrace();
               }



            }
        }).start();

    }


    private static JSONObject getJson(Response response) throws JSONException {
        if(response.getStatus() != 200){   // 请求不成功
            String wro =new String(response.getContent());
            JSONObject json = new JSONObject(wro);
            Log.e("response1", wro);
            return json;
        }
        String res = new String(response.getContent());
        Log.e("response2", res);
        JSONObject json = new JSONObject(res);
        return json;

    }
}
