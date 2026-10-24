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
import android.widget.Toast;
import java.io.OutputStream;
public class MainActivity extends Activity {
 private WebView web;
 private ValueCallback<Uri[]> fileCallback;
 private static final int PICK_FILE=91;
 @Override public void onCreate(Bundle state){super.onCreate(state);
 web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowFileAccessFromFileURLs(true);s.setAllowUniversalAccessFromFileURLs(false);
 web.addJavascriptInterface(new Bridge(),"AndroidSave");
 web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;try{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Choose image"),PICK_FILE);return true;}catch(Exception e){fileCallback=null;return false;}}});
 web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{if(url.startsWith("data:image/")){new Bridge().saveJpg(url,"SupremeSeed_Report.jpg");}});
 web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){v.evaluateJavascript("(function(){const old=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){let link=this;if(link.href.startsWith('blob:')){fetch(link.href).then(r=>r.blob()).then(b=>{let fr=new FileReader();fr.onload=()=>AndroidSave.saveJpg(String(fr.result),link.download||'SupremeSeed_Report.jpg');fr.readAsDataURL(b)});return;}if(link.href.startsWith('data:image/')){AndroidSave.saveJpg(link.href,link.download||'SupremeSeed_Report.jpg');return;}return old.call(link)}})();",null);}});
 web.loadUrl("file:///android_asset/index.html");}
 public class Bridge{@JavascriptInterface public void saveJpg(String dataUrl,String filename){try{String safe=filename.replaceAll("[^a-zA-Z0-9._-]","_");if(!safe.toLowerCase().endsWith(".jpg"))safe+=".jpg";byte[] bytes=Base64.decode(dataUrl.substring(dataUrl.indexOf(',')+1),Base64.DEFAULT);ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,safe);values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(android.os.Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/SupremeSeed");Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new Exception("Cannot save");try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(bytes);}runOnUiThread(()->Toast.makeText(MainActivity.this,"Saved to Pictures/SupremeSeed",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue((result==RESULT_OK&&data!=null&&data.getData()!=null)?new Uri[]{data.getData()}:null);fileCallback=null;}}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
