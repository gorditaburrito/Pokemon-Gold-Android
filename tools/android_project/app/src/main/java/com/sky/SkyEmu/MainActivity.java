package com.sky.SkyEmu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** RetroForge V2 builder prototype. Produces a validated build bundle, NOT an APK. */
public class MainActivity extends Activity {
    private static final int ROM=101, TEMPLATE=102, EXPORT=103;
    private Uri rom, template;
    private String romName="", title="", extension="", packageId="";
    private TextView status;
    private void message(String s) { status.setText(s); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(18*getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        TextView heading=new TextView(this); heading.setText("RetroForge V2 — Builder prototype");heading.setTextSize(22);root.addView(heading);
        status=new TextView(this);status.setText("Choose a ROM and a known-good SkyEmu APK.\nThis prototype exports a build bundle; it does not yet generate an installable APK.");root.addView(status);
        Button chooseRom=new Button(this);chooseRom.setText("1. Choose ROM");chooseRom.setOnClickListener(v->pick(ROM,"*/*"));root.addView(chooseRom);
        Button chooseTemplate=new Button(this);chooseTemplate.setText("2. Choose working APK");chooseTemplate.setOnClickListener(v->pick(TEMPLATE,"application/vnd.android.package-archive"));root.addView(chooseTemplate);
        Button export=new Button(this);export.setText("3. Export build bundle");export.setOnClickListener(v->{if(rom==null||template==null){message("Select both files first.");return;} Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"retroforge-"+packageId+".zip");startActivityForResult(i,EXPORT);});root.addView(export);
        setContentView(root);
    }
    private void pick(int req,String mime){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);if(req==TEMPLATE)i.setType("*/*");startActivityForResult(i,req);}
    private String name(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "";}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{
        if(req==ROM){Uri u=data.getData();String n=name(u);String l=n.toLowerCase(Locale.ROOT);String ext=l.endsWith(".gbc")?"gbc":l.endsWith(".gba")?"gba":l.endsWith(".gb")?"gb":null;if(ext==null)throw new IOException("ROM must be .gb, .gbc or .gba");
            byte[] header=new byte[192];int got=0;try(InputStream in=getContentResolver().openInputStream(u)){while(got<header.length){int r=in.read(header,got,header.length-got);if(r<0)break;got+=r;}}if(got<192)throw new IOException("ROM header too short");
            String raw="";int off=ext.equals("gba")?0xA0:0x134;int len=ext.equals("gba")?12:15;for(int j=0;j<len;j++){int ch=header[off+j]&255;if(ch==0)break;if(ch>=32&&ch<=126)raw+=(char)ch;}if(raw.trim().isEmpty())raw=n.substring(0,n.lastIndexOf('.'));
            String slug=raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");if(slug.isEmpty())slug="game";rom=u;romName=n;extension=ext;title=raw.trim();packageId=slug;message("ROM: "+romName+"\nDetected title: "+title+"\nGame ID: "+packageId+(template==null?"\nChoose APK template next.":"\nReady to export."));
        }else if(req==TEMPLATE){Uri u=data.getData();boolean manifest=false,dex=false;try(ZipInputStream z=new ZipInputStream(getContentResolver().openInputStream(u))){ZipEntry e;while((e=z.getNextEntry())!=null){if(e.getName().equals("AndroidManifest.xml"))manifest=true;if(e.getName().equals("classes.dex"))dex=true;}}if(!manifest||!dex)throw new IOException("Selected file is not a valid Android APK template");template=u;message("Template accepted: "+name(u)+"\n"+(rom==null?"Choose ROM next.":"Ready to export."));
        }else if(req==EXPORT){if(rom==null||template==null)throw new IOException("Missing inputs");Uri out=data.getData();try(ZipOutputStream z=new ZipOutputStream(getContentResolver().openOutputStream(out))){addText(z,"retroforge.properties","title="+title.replace("\n", " ")+"\nslug="+packageId+"\npackageId=org.retroforge.game."+packageId+"\nextension="+extension+"\n");addStream(z,"game."+extension,rom);addStream(z,"template.apk",template);}message("Build bundle exported. This ZIP is NOT an installable APK. Next: compiled manifest/resource editing and APK signing.");}
    }catch(Exception ex){new AlertDialog.Builder(this).setTitle("RetroForge").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}}
    private void addText(ZipOutputStream z,String name,String content)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(content.getBytes("UTF-8"));z.closeEntry();}
    private void addStream(ZipOutputStream z,String name,Uri u)throws IOException{z.putNextEntry(new ZipEntry(name));try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Cannot read "+name);byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>512L*1024*1024)throw new IOException("Input exceeds 512 MB");z.write(b,0,n);}}z.closeEntry();}
}
