package com.supremeseed.fieldapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.webkit.*;
import android.net.Uri;
import android.content.Intent;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
 private WebView web;
 private FrameLayout root;
 private ValueCallback<Uri[]> fileCallback;
 private static final int PICK_FILE=91;
 private boolean exportBusy=false;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  // Must be enabled before any WebView is constructed, for full-document captures.
  WebView.enableSlowWholeDocumentDraw();
  root=new FrameLayout(this);setContentView(root);
  web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true);s.setAllowFileAccessFromFileURLs(true);s.setAllowUniversalAccessFromFileURLs(false);
  web.addJavascriptInterface(new Bridge(),"AndroidSave");
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
    try{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
     startActivityForResult(Intent.createChooser(i,"Choose image"),PICK_FILE);return true;
    }catch(Exception e){fileCallback=null;return false;}
   }
  });
  web.setWebViewClient(new WebViewClient());
  web.loadUrl("file:///android_asset/index.html");
 }

 private void toast(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
 private void writeJpeg(byte[] bytes,String filename) throws Exception{
  String safe=filename.replaceAll("[^a-zA-Z0-9._-]","_");
  if(!safe.toLowerCase().endsWith(".jpg"))safe+=".jpg";
  ContentValues values=new ContentValues();
  values.put(MediaStore.Images.Media.DISPLAY_NAME,safe);
  values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
  values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/SupremeSeed");
  values.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
  if(uri==null)throw new Exception("Cannot create image file");
  try{
   try(OutputStream out=getContentResolver().openOutputStream(uri)){
    if(out==null)throw new Exception("Cannot open image file");out.write(bytes);
   }
   ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);
   getContentResolver().update(uri,done,null,null);
  }catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
 }

 public class Bridge {
  @JavascriptInterface public void saveJpg(String dataUrl,String filename){
   new Thread(()->{try{byte[] bytes=Base64.decode(dataUrl.substring(dataUrl.indexOf(',')+1),Base64.DEFAULT);
    writeJpeg(bytes,filename);toast("JPG saved: Pictures/SupremeSeed");}
    catch(Exception e){toast("Save failed: "+e.getMessage());}}).start();
  }
  @JavascriptInterface public void saveReportHtml(String html,String filename){
   runOnUiThread(()->captureHtml(html,filename));
  }
 }

 private void captureHtml(String html,String filename){
  if(exportBusy){toast("A report is already exporting");return;}
  exportBusy=true;
  WebView capture=new WebView(this);
  capture.setBackgroundColor(Color.WHITE);
  capture.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  WebSettings cs=capture.getSettings();cs.setJavaScriptEnabled(true);cs.setDefaultTextEncodingName("UTF-8");
  cs.setAllowFileAccess(true);cs.setAllowFileAccessFromFileURLs(true);
  cs.setAllowUniversalAccessFromFileURLs(false);
  // Place below the visible WebView: the capture WebView stays attached to a window.
  root.addView(capture,0,new FrameLayout.LayoutParams(760,1));
  final boolean[] completed={false};
  Runnable cleanup=()->{if(!completed[0]){completed[0]=true;root.removeView(capture);capture.destroy();exportBusy=false;}};
  capture.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String url){
    capture.postDelayed(()->capture.evaluateJavascript("Math.ceil(Math.max(document.body.scrollHeight,document.documentElement.scrollHeight))",result->{
     try{
      int height=(int)Math.ceil(Double.parseDouble(result.replace("\"","")));
      if(height<1||height>14000)throw new Exception("Report height is unsupported: "+height);
      int width=760;
      if((long)width*height>12000000L)throw new Exception("Report too large for one JPG");
      capture.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
      capture.layout(0,0,width,height);
      capture.postDelayed(()->{
       if(completed[0])return;
       try{
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);capture.draw(canvas);
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,buffer))throw new Exception("JPG encoding failed");
        bitmap.recycle();byte[] bytes=buffer.toByteArray();cleanup.run();
        new Thread(()->{try{writeJpeg(bytes,filename);toast("JPG saved: Pictures/SupremeSeed");}
          catch(Exception e){toast("JPG save failed: "+e.getMessage());}}).start();
       }catch(Exception e){cleanup.run();toast("JPG export failed: "+e.getMessage());}
      },350);
     }catch(Exception e){cleanup.run();toast("JPG export failed: "+e.getMessage());}
    }),1200);
   }
   @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){
    if(request.isForMainFrame()){cleanup.run();toast("Report could not be loaded");}
   }
  });
  capture.loadDataWithBaseURL("file:///android_asset/",html,"text/html","UTF-8",null);
 }

 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue((result==RESULT_OK&&data!=null&&data.getData()!=null)?new Uri[]{data.getData()}:null);fileCallback=null;}
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
