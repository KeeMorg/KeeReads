package com.keereads.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local, document-specific editable sidecar annotations. Export flattens them. */
final class Annotations {
    static final String PEN="pen", HIGHLIGHT="highlight", UNDERLINE="underline", WAVE="wave", TEXT="text", EXACT_HIGHLIGHT="text-highlight", EXACT_UNDERLINE="text-underline", EXACT_WAVE="text-wave", FLASHCARD="flashcard";
    private final File file;
    private JSONObject state = initial();
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();
    private final Set<Integer> bookmarks = new HashSet<>();

    Annotations(Context ctx, String documentUri) {
        file = new File(ctx.getFilesDir(), "notes_" + digest(documentUri) + ".json");
        if(file.exists()) try(FileInputStream in = new FileInputStream(file)) {
            state=new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } catch(Exception ignored) { state=initial(); }
        syncBookmarks();
    }
    private static byte[] readAll(FileInputStream in) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[4096]; int n;
        while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
        return out.toByteArray();
    }
    private static JSONObject initial() {
        JSONObject o=new JSONObject();
        try { o.put("version",1);o.put("items",new JSONArray());o.put("bookmarks",new JSONArray());o.put("lastPage",0); }
        catch(JSONException ignored) {} return o;
    }
    private static String digest(String input) {
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder(); for(int i=0;i<12;i++) b.append(String.format("%02x", hash[i] & 255)); return b.toString();
        } catch(Exception e){return Integer.toHexString(input.hashCode());}
    }
    private void beforeEdit() {undo.push(state.toString());while(undo.size()>50) undo.removeLast();redo.clear();}
    private void syncBookmarks() {
        bookmarks.clear();JSONArray a=state.optJSONArray("bookmarks");
        if(a!=null)for(int i=0;i<a.length();i++)bookmarks.add(a.optInt(i));
    }
    private void save(){
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(state.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!temp.renameTo(file)) {
                try (FileOutputStream direct = new FileOutputStream(file)) {
                    direct.write(state.toString().getBytes(StandardCharsets.UTF_8));
                    direct.getFD().sync();
                }
                temp.delete();
            }
        } catch (Exception ignored) {
            // Keep the last intact sidecar if disk writes fail.
        }
    }
    int getLastPage(){return state.optInt("lastPage",0);}
    void setLastPage(int page){try{state.put("lastPage",page);save();}catch(JSONException ignored){}}
    boolean isBookmarked(int page){return bookmarks.contains(page);}
    Set<Integer> bookmarks(){return new HashSet<>(bookmarks);}
    void toggleBookmark(int page){
        beforeEdit();if(!bookmarks.add(page))bookmarks.remove(page);
        JSONArray array=new JSONArray();
        java.util.ArrayList<Integer> sorted = new java.util.ArrayList<>(bookmarks);
        java.util.Collections.sort(sorted);
        for (int p : sorted) array.put(p);
        try{state.put("bookmarks",array);save();}catch(JSONException ignored){}
    }
    void add(Item item){
        JSONArray items=state.optJSONArray("items");
        if (items==null) return;
        beforeEdit();items.put(item.json);save();
    }
    void remove(Item item){beforeEdit();
        JSONArray old=state.optJSONArray("items"), next=new JSONArray();
        if (old==null) return;
        for (int i=0;i<old.length();i++) {
            JSONObject candidate=old.optJSONObject(i);
            if (candidate==null || !item.id().equals(candidate.optString("id"))) next.put(old.opt(i));
        }
        try{state.put("items",next);save();}catch(JSONException ignored){}
    }
    void replace(Item item){
        JSONArray a=state.optJSONArray("items");
        if (a==null) return;
        for (int i=0;i<a.length();i++) {
            JSONObject old=a.optJSONObject(i);
            if (old!=null && item.id().equals(old.optString("id"))) {
                beforeEdit();
                try { a.put(i,item.json);save(); } catch (JSONException ignored) {}
                break;
            }
        }
    }
    boolean undo(){if(undo.isEmpty())return false;redo.push(state.toString());
        try{state=new JSONObject(undo.pop());syncBookmarks();save();return true;}catch(Exception ignored){return false;}}
    boolean redo(){if(redo.isEmpty())return false;undo.push(state.toString());
        try{state=new JSONObject(redo.pop());syncBookmarks();save();return true;}catch(Exception ignored){return false;}}
    List<Item> all(){List<Item> out=new ArrayList<>();JSONArray a=state.optJSONArray("items");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item!=null)out.add(new Item(item));}return out;}
    List<Item> onPage(int page){List<Item> out=new ArrayList<>();for(Item i:all())if(i.page()==page)out.add(i);return out;}

    static final class Item {
        JSONObject json;
        Item(JSONObject json){this.json=json;}
        static Item newItem(int page,String type,int color,float width,float opacity){
            JSONObject o=new JSONObject();try{
                o.put("id",java.util.UUID.randomUUID().toString());o.put("page",page);o.put("type",type);
                o.put("color",color);o.put("width",width);o.put("opacity",opacity);o.put("points",new JSONArray());
                o.put("created",System.currentTimeMillis());
            }catch(JSONException ignored){}return new Item(o);
        }
        String id(){return json.optString("id");}
        int page(){return json.optInt("page");}
        String type(){return json.optString("type");}
        int color(){return json.optInt("color",Color.YELLOW);}
        float opacity(){return (float)json.optDouble("opacity",1);}
        float width(){return (float)json.optDouble("width",.003);}
        String text(){return json.optString("text","");}
        String answer(){return json.optString("answer","");}
        void setAnswer(String s){try{json.put("answer",s);}catch(JSONException ignored){}}
        void addBox(RectF box){JSONArray arr=json.optJSONArray("boxes");if(arr==null){arr=new JSONArray();try{json.put("boxes",arr);}catch(JSONException ignored){}}
            JSONArray coordinates=new JSONArray();
            try {
                coordinates.put(box.left);
                coordinates.put(box.top);
                coordinates.put(box.right);
                coordinates.put(box.bottom);
            } catch (JSONException ignored) { return; }
            arr.put(coordinates);
        }
        List<RectF> boxes(){List<RectF> out=new ArrayList<>();JSONArray a=json.optJSONArray("boxes");if(a!=null)for(int i=0;i<a.length();i++){
            JSONArray box=a.optJSONArray(i);if(box!=null)out.add(new RectF((float)box.optDouble(0),(float)box.optDouble(1),(float)box.optDouble(2),(float)box.optDouble(3)));
        }return out;}
        long created(){return json.optLong("created",0);}
        void setText(String s){try{json.put("text",s);}catch(JSONException ignored){}}
        void setColor(int color){try{json.put("color",color);}catch(JSONException ignored){}}
        void move(float dx,float dy){
            JSONArray a=json.optJSONArray("points");if(a==null)return;
            for(int i=0;i<a.length();i++){JSONArray p=a.optJSONArray(i);if(p!=null)try{
                p.put(0, Math.max(0,Math.min(1,p.optDouble(0)+dx)));
                p.put(1, Math.max(0,Math.min(1,p.optDouble(1)+dy)));
            }catch(JSONException ignored){}}
            JSONArray boxes=json.optJSONArray("boxes");if(boxes!=null)for(int i=0;i<boxes.length();i++){
                JSONArray r=boxes.optJSONArray(i);if(r!=null)for(int j=0;j<4;j++)try{
                    r.put(j, Math.max(0,Math.min(1,r.optDouble(j)+(j%2==0?dx:dy))));
                }catch(JSONException ignored){}
            }
        }
        void add(float x,float y){
            JSONArray p=new JSONArray();
            try {
                p.put(Math.max(0,Math.min(1,x)));
                p.put(Math.max(0,Math.min(1,y)));
            } catch (JSONException ignored) { return; }
            json.optJSONArray("points").put(p);
        }
        List<PointF> points(){List<PointF> pts=new ArrayList<>();JSONArray a=json.optJSONArray("points");
            if(a!=null)for(int i=0;i<a.length();i++) {JSONArray p=a.optJSONArray(i);if(p!=null)pts.add(new PointF((float)p.optDouble(0),(float)p.optDouble(1)));}
            return pts;
        }
        PointF center(){List<PointF> p=points();if(p.isEmpty()){List<RectF> r=boxes();return r.isEmpty()?new PointF():new PointF(r.get(0).centerX(),r.get(0).centerY());}float x=0,y=0;for(PointF pt:p){x+=pt.x;y+=pt.y;}return new PointF(x/p.size(),y/p.size());}
        void draw(Canvas canvas,float w,float h,boolean selected){
            List<PointF> pts=points(); if(pts.isEmpty() && boxes().isEmpty())return;
            if(FLASHCARD.equals(type()))return;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color());p.setAlpha(Math.round(opacity()*255));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(1,width()*w));
            String t=type();
            if(t.startsWith("text-") && !TEXT.equals(t)) {
                List<RectF> boxes=boxes();
                for(RectF r:boxes){float x1=r.left*w,x2=r.right*w,y1=r.top*h,y2=r.bottom*h;
                    if(EXACT_HIGHLIGHT.equals(t)){p.setStyle(Paint.Style.FILL);canvas.drawRect(x1,y1,x2,y2,p);}
                    else {p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,width()*w));
                        if(EXACT_UNDERLINE.equals(t))canvas.drawLine(x1,y2,x2,y2,p);
                        else {Path wave=new Path();float length=x2-x1;int steps=Math.max(4,(int)(length/4));
                            for(int j=0;j<=steps;j++){float fraction=j/(float)steps;float yy=y2+(float)Math.sin(fraction*length/4*Math.PI)*2;
                                if(j==0)wave.moveTo(x1,yy);else wave.lineTo(x1+fraction*length,yy);
                            }canvas.drawPath(wave,p);
                        }
                    }
                }
            } else if(TEXT.equals(t)){
                p.setStyle(Paint.Style.FILL);p.setTextSize(Math.max(10,width()*w*7));
                float x=pts.get(0).x*w,y=pts.get(0).y*h;
                for(String line:text().split("\\n",-1)){canvas.drawText(line,x,y,p);y+=p.getTextSize()*1.3f;}
            }else if(HIGHLIGHT.equals(t)){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(9,width()*w));
                Path path=new Path();path.moveTo(pts.get(0).x*w,pts.get(0).y*h);
                for(int i=1;i<pts.size();i++)path.lineTo(pts.get(i).x*w,pts.get(i).y*h);
                canvas.drawPath(path,p);
            } else if(UNDERLINE.equals(t) || WAVE.equals(t)){
                p.setStyle(Paint.Style.STROKE);
                PointF a=pts.get(0), b=pts.get(pts.size()-1);
                float x1=a.x*w,y1=a.y*h,x2=b.x*w,y2=b.y*h;
                if(WAVE.equals(t)){
                    Path path=new Path();float dx=x2-x1,dy=y2-y1,d=(float)Math.hypot(dx,dy);
                    int n=Math.max(2,(int)(d/5));for(int i=0;i<=n;i++){
                        float f=i/(float)n;float nx=x1+dx*f,ny=y1+dy*f+(float)Math.sin(f*d/5*Math.PI)*3;
                        if(i==0)path.moveTo(nx,ny);else path.lineTo(nx,ny);
                    }canvas.drawPath(path,p);
                }else canvas.drawLine(x1,y1,x2,y2,p);
            } else {
                p.setStyle(Paint.Style.STROKE);Path path=new Path();
                path.moveTo(pts.get(0).x*w,pts.get(0).y*h);
                for(int i=1;i<pts.size();i++)path.lineTo(pts.get(i).x*w,pts.get(i).y*h);
                if(pts.size()==1)canvas.drawPoint(pts.get(0).x*w,pts.get(0).y*h,p);else canvas.drawPath(path,p);
            }
            if(selected){PointF c=center();p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(55,93,236));p.setAlpha(255);p.setStrokeWidth(2);
                canvas.drawCircle(c.x*w,c.y*h,16,p);}
        }
    }
}
