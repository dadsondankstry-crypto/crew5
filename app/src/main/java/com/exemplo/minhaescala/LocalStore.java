package com.exemplo.minhaescala;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Banco privado da aplicacao: nao usa rede nem permissao de armazenamento global. */
public final class LocalStore {
    public static final class Line {
        public String text = "";
        public int left, top, right, bottom;
        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("text",text); j.put("l",left); j.put("t",top);
            j.put("r",right); j.put("b",bottom); return j;
        }
        static Line fromJson(JSONObject j) {
            Line v = new Line(); v.text = j.optString("text","");
            v.left=j.optInt("l");v.top=j.optInt("t");
            v.right=j.optInt("r");v.bottom=j.optInt("b");return v;
        }
        public int cy() { return (top+bottom)/2; }
    }
    public static final class Photo {
        public String id="", file="", title="", date="", kind="", raw="", corrected="", sha256="";
        public int width, height;
        public boolean kindManual=false;
        public ArrayList<Line> lines = new ArrayList<>();
        JSONObject toJson() throws Exception {
            JSONObject j=new JSONObject();
            j.put("id",id);j.put("file",file);j.put("title",title);
            j.put("date",date);j.put("kind",kind);j.put("raw",raw);
            j.put("corrected",corrected);j.put("sha256",sha256);j.put("kindManual",kindManual);j.put("width",width);j.put("height",height);
            JSONArray a=new JSONArray();for(Line l:lines)a.put(l.toJson());j.put("lines",a);return j;
        }
        static Photo fromJson(JSONObject j) {
            Photo p=new Photo();p.id=j.optString("id");p.file=j.optString("file");
            p.title=j.optString("title");p.date=j.optString("date");
            p.kind=j.optString("kind");p.raw=j.optString("raw");
            p.corrected=j.optString("corrected");p.sha256=j.optString("sha256","");p.kindManual=j.optBoolean("kindManual",false);p.width=j.optInt("width");p.height=j.optInt("height");
            JSONArray a=j.optJSONArray("lines");if(a!=null)for(int i=0;i<a.length();i++){
                JSONObject item=a.optJSONObject(i);if(item!=null)p.lines.add(Line.fromJson(item));
            }return p;
        }
    }
    public static final class Shift {
        public String id="", person="", mscId="", date="", start="", end="", start2="", end2="",
                break1="",break2="",training="",section="", notes="", source="";
        JSONObject toJson() throws Exception {
            JSONObject j=new JSONObject();j.put("id",id);j.put("person",person);j.put("date",date);
            j.put("start",start);j.put("end",end);j.put("start2",start2);j.put("end2",end2);
            j.put("break1",break1);j.put("break2",break2);j.put("training",training);j.put("mscId",mscId);
            j.put("section",section);
            j.put("notes",notes);j.put("source",source);return j;
        }
        static Shift fromJson(JSONObject j) {
            Shift s=new Shift();s.id=j.optString("id");s.person=j.optString("person");
            s.date=j.optString("date");s.start=j.optString("start");
            s.end=j.optString("end");s.start2=j.optString("start2","");s.end2=j.optString("end2","");
            s.break1=j.optString("break1","");s.break2=j.optString("break2","");
            s.training=j.optString("training","");s.mscId=j.optString("mscId","");s.section=j.optString("section");
            s.notes=j.optString("notes");s.source=j.optString("source");return s;
        }
    }
    public final ArrayList<Photo> photos = new ArrayList<>();
    public final ArrayList<Shift> shifts = new ArrayList<>();
    public final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private final File dataFile;
    public LocalStore(Context context) {dataFile=new File(context.getFilesDir(),"minha_escala.json");load();}
    private void load() {
        if(!dataFile.isFile())return;
        try(FileInputStream in=new FileInputStream(dataFile)) {
            byte[] data = new byte[(int)Math.min(dataFile.length(),10_000_000)];
            int used=0;while(used<data.length){int n=in.read(data,used,data.length-used);if(n<0)break;used+=n;}
            JSONObject j=new JSONObject(new String(data,0,used,StandardCharsets.UTF_8));
            JSONArray p=j.optJSONArray("photos");if(p!=null)for(int i=0;i<p.length();i++){
                JSONObject v=p.optJSONObject(i);if(v!=null)photos.add(Photo.fromJson(v));
            }
            JSONArray s=j.optJSONArray("shifts");if(s!=null)for(int i=0;i<s.length();i++){
                JSONObject v=s.optJSONObject(i);if(v!=null)shifts.add(Shift.fromJson(v));
            }
            JSONArray f=j.optJSONArray("favorites");if(f!=null)for(int i=0;i<f.length();i++){
                String v=f.optString(i,"").trim();if(!v.isEmpty())favorites.add(v);
            }
        }catch(Exception ignored){ /* Dados existentes nao sao apagados em caso de erro. */ }
    }
    public synchronized boolean save() {
        try {
            JSONObject j=new JSONObject();JSONArray p=new JSONArray(),s=new JSONArray(),f=new JSONArray();
            for(Photo v:photos)p.put(v.toJson());for(Shift v:shifts)s.put(v.toJson());
            for(String v:favorites)f.put(v);j.put("photos",p);j.put("shifts",s);j.put("favorites",f);
            File tmp=new File(dataFile.getParentFile(),"minha_escala.tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){
                out.write(j.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            if(!tmp.renameTo(dataFile)) {
                try(FileInputStream in=new FileInputStream(tmp);FileOutputStream out=new FileOutputStream(dataFile)){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);
                }tmp.delete();
            }
            return true;
        }catch(Exception ex){return false;}
    }
}
