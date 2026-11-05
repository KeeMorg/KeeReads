package com.keereads.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Canvas-based paginated reader and normalized annotation overlay. */
public final class PdfSurface extends View {
    public interface Listener {void onPageChanged(int page);void onPlaceText(int page,float x,float y);void onSelection(Annotations.Item item);void onAnnotationsChanged();void onTextSelected(int page,List<RectF> boxes,String selectedText);}
    public enum Tool {PAN,PEN,HIGHLIGHT,UNDERLINE,WAVE,ERASER,SELECT,TEXT,EXACT}
    public enum Mode {CONTINUOUS,SINGLE,SPREAD}
    public enum Theme {WHITE,SEPIA,DARK}
    private volatile PdfRenderer renderer;
    private volatile int documentGeneration=0;
    private final ExecutorService pageRenderWorker=Executors.newSingleThreadExecutor();
    // Modified only on the UI thread; worker results are marshalled back using post().
    private final Set<Integer> pagesBeingRendered=new HashSet<>();
    private Annotations annotations;
    private Listener listener;
    private Tool tool=Tool.PAN;
    private Mode mode=Mode.CONTINUOUS;
    private Theme theme=Theme.WHITE;
    private int currentPage=0, color=Color.rgb(255,210,44);
    private float penWidth=.0035f, markerWidth=.027f, highlightOpacity=.42f;
    private float zoom=1, panX=0,panY=0,totalHeight=0,totalWidth=0;
    private float prevX,prevY,lastNormX,lastNormY;
    private boolean zooming=false, dragging=false;
    private int activePage=-1;
    private Annotations.Item inProgress, selected;
    private RectF selectionPreview;
    private final List<RectF> pageRects=new ArrayList<>();
    private final List<PointF> pageDimensions=new ArrayList<>();
    private final Map<Integer,List<RectF>> searchMatches=new HashMap<>();
    private final Paint imgPaint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
    private final Paint searchPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final LruCache<Integer,Bitmap> bitmapCache=new LruCache<Integer,Bitmap>(32*1024){
        @Override protected int sizeOf(Integer key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}
        @Override protected void entryRemoved(boolean evicted,Integer key,Bitmap oldValue,Bitmap newValue){
            if(oldValue!=newValue && !oldValue.isRecycled())oldValue.recycle();
        }
    };

    public PdfSurface(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        scaleDetector=new ScaleGestureDetector(ctx,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                float old=zoom;zoom=clamp(zoom*d.getScaleFactor(),.7f,4.5f);
                float f=d.getFocusX(),g=d.getFocusY();
                panX=(panX+f)/old*zoom-f;panY=(panY+g)/old*zoom-g;
                constrainScroll();invalidate();return true;
            }
        });
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    public void setListener(Listener l){listener=l;}
    public void open(PdfRenderer r,Annotations a){
        documentGeneration++;
        pagesBeingRendered.clear();
        bitmapCache.evictAll();renderer=r;annotations=a;currentPage=0;zoom=1;panX=panY=0;
        pageDimensions.clear();pageRects.clear();searchMatches.clear();selected=null;
        if(r!=null)for(int i=0;i<r.getPageCount();i++){
            try(PdfRenderer.Page page=r.openPage(i)){pageDimensions.add(new PointF(Math.max(1,page.getWidth()),Math.max(1,page.getHeight())));}
        }
        requestLayout();invalidate();
    }
    public int pageCount(){return renderer==null?0:renderer.getPageCount();}
    public int getPage(){return currentPage;}
    public void setTool(Tool t){tool=t;selected=null;invalidate();}
    public Tool getTool(){return tool;}
    public void setMode(Mode m){mode=m;panX=panY=0;layoutPages();invalidate();}
    public Mode getMode(){return mode;}
    public void setTheme(Theme t){theme=t;invalidate();}
    public Theme getTheme(){return theme;}
    public void setColor(int c){color=c;}
    public int getColor(){return color;}
    public void setWidth(float w){penWidth=w;}
    public void setOpacity(float v){highlightOpacity=v;}
    public void zoomBy(float factor){zoom=clamp(zoom*factor,.7f,4.5f);constrainScroll();invalidate();}
    public void resetZoom(){zoom=1;panX=panY=0;invalidate();}
    public void setPage(int p){if(renderer==null)return;
        int next=Math.max(0,Math.min(p,renderer.getPageCount()-1));currentPage=next;
        layoutPages();
        if(mode==Mode.CONTINUOUS && next<pageRects.size())panY=Math.max(0,pageRects.get(next).top*zoom-dp(12));
        else panY=0;
        constrainScroll();invalidate();if(listener!=null)listener.onPageChanged(next);
    }
    public void stepPage(int delta){setPage(currentPage+(mode==Mode.SPREAD?delta*2:delta));}
    public void setSearchMatches(Map<Integer,List<RectF>> matches){searchMatches.clear();searchMatches.putAll(matches);invalidate();}
    public void clearSearch(){searchMatches.clear();invalidate();}
    public Annotations.Item getSelected(){return selected;}
    public void deselect(){selected=null;invalidate();}
    public void refresh(){invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);layoutPages();}
    private void layoutPages(){
        pageRects.clear();totalWidth=getWidth();totalHeight=getHeight();if(renderer==null||getWidth()<=0)return;
        float margin=dp(12),gap=dp(12),top=gap;
        float columnWidth=(mode==Mode.SPREAD?(getWidth()-gap*3)/2:(getWidth()-gap*2));
        int visibleStart=mode==Mode.SPREAD?(currentPage/2)*2:currentPage;
        for(int i=0;i<renderer.getPageCount();i++){
            PointF size=pageDimensions.get(i);
            float scale=columnWidth/size.x;
            float w=columnWidth,h=size.y*scale;
            float x=margin;
            if(mode==Mode.SPREAD){
                x=margin+(i%2)*(columnWidth+gap);
                top=gap;
            } else if(mode==Mode.SINGLE){top=gap;}
            RectF rect=new RectF(x,top,x+w,top+h);
            pageRects.add(rect);
            if(mode==Mode.CONTINUOUS)top+=h+gap;
            if(mode==Mode.SPREAD && i%2==1){totalHeight=Math.max(totalHeight,top+h+gap);}
        }
        if(mode==Mode.CONTINUOUS)totalHeight=top;
        if(mode==Mode.SINGLE)totalHeight=pageRects.get(visibleStart).bottom+gap;
        if(mode==Mode.SPREAD){totalHeight=pageRects.get(visibleStart).bottom+gap;
            if(visibleStart+1<pageRects.size())totalHeight=Math.max(totalHeight,pageRects.get(visibleStart+1).bottom+gap);}
        constrainScroll();
    }
    private void constrainScroll(){panX=clamp(panX,0,Math.max(0,totalWidth*zoom-getWidth()));
        float scrollHeight=totalHeight;
        if(mode==Mode.SINGLE && !pageRects.isEmpty())scrollHeight=pageRects.get(currentPage).bottom+dp(12);
        if(mode==Mode.SPREAD && !pageRects.isEmpty()){
            int start=currentPage/2*2;scrollHeight=pageRects.get(start).bottom+dp(12);
            if(start+1<pageRects.size())scrollHeight=Math.max(scrollHeight,pageRects.get(start+1).bottom+dp(12));
        }
        panY=clamp(panY,0,Math.max(0,scrollHeight*zoom-getHeight()));
    }
    private static float clamp(float x,float min,float max){return Math.max(min,Math.min(max,x));}
    private boolean visiblePage(int i){return mode==Mode.CONTINUOUS || (mode==Mode.SINGLE && i==currentPage) || (mode==Mode.SPREAD && i/2==currentPage/2);}
    @Override protected void onDraw(Canvas cv){super.onDraw(cv);
        cv.drawColor(theme==Theme.DARK?Color.rgb(18,23,35):theme==Theme.SEPIA?Color.rgb(224,215,196):Color.rgb(231,235,244));
        if(renderer==null){Paint p=new Paint(3);p.setColor(theme==Theme.DARK?Color.WHITE:Color.rgb(70,80,111));
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(18));cv.drawText("Open a PDF to begin reading",getWidth()/2f,getHeight()/2f,p);return;}
        cv.save();cv.translate(-panX,-panY);cv.scale(zoom,zoom);
        for(int i=0;i<pageRects.size();i++){
            if(!visiblePage(i))continue;
            RectF r=pageRects.get(i);
            if(r.bottom*zoom<panY-dp(100)||r.top*zoom>panY+getHeight()+dp(100))continue;
            Paint paper=new Paint(3);paper.setColor(theme==Theme.DARK?Color.rgb(30,35,51):theme==Theme.SEPIA?Color.rgb(255,248,225):Color.WHITE);
            cv.drawRoundRect(r,dp(2),dp(2),paper);
            Bitmap bitmap=getBitmap(i);
            if(bitmap!=null){imgPaint.setColorFilter(null);
                if(theme==Theme.DARK){ColorMatrix mx=new ColorMatrix(new float[]{-1,0,0,0,255,0,-1,0,0,255,0,0,-1,0,255,0,0,0,1,0});imgPaint.setColorFilter(new ColorMatrixColorFilter(mx));}
                else if(theme==Theme.SEPIA){ColorMatrix mx=new ColorMatrix(new float[]{.93f,0,0,0,15,0,.90f,0,0,9,0,0,.78f,0,1,0,0,0,1,0});imgPaint.setColorFilter(new ColorMatrixColorFilter(mx));}
                cv.drawBitmap(bitmap,null,r,imgPaint);imgPaint.setColorFilter(null);
            }
            cv.save();cv.translate(r.left,r.top);
            List<RectF> matches=searchMatches.get(i);if(matches!=null){searchPaint.setColor(0x66FF9900);
                for(RectF match:matches){float sx=r.width()/pageDimensions.get(i).x,sy=r.height()/pageDimensions.get(i).y;
                    cv.drawRect(match.left*sx,match.top*sy,match.right*sx,match.bottom*sy,searchPaint);}}
            if(annotations!=null)for(Annotations.Item a:annotations.onPage(i))a.draw(cv,r.width(),r.height(),a.id().equals(selected==null?"":selected.id()));
            if(activePage==i && selectionPreview!=null){Paint selectionPaint=new Paint(3);selectionPaint.setColor(0x554056CC);
                cv.drawRect(selectionPreview.left*r.width(),selectionPreview.top*r.height(),selectionPreview.right*r.width(),selectionPreview.bottom*r.height(),selectionPaint);
            }
            if(inProgress!=null && activePage==i)inProgress.draw(cv,r.width(),r.height(),false);
            cv.restore();
        }
        cv.restore();
    }
    private Bitmap getBitmap(int i){
        Bitmap cached=bitmapCache.get(i);
        if(cached!=null && !cached.isRecycled())return cached;
        final PdfRenderer source=renderer;
        if(source==null || !pagesBeingRendered.add(i))return null;
        final int generation=documentGeneration;
        pageRenderWorker.execute(()->{
            Bitmap result=null;
            try {
                synchronized(source) {
                    if (renderer==source && documentGeneration==generation) {
                        try (PdfRenderer.Page page=source.openPage(i)) {
                            int w=Math.max(1,page.getWidth()),h=Math.max(1,page.getHeight());
                            float factor=Math.min(2.4f,1800f/w);
                            factor=Math.min(factor,(float)Math.sqrt(2800000d/((double)w*h)));
                            int rw=Math.max(1,Math.round(w*factor));
                            int rh=Math.max(1,Math.round(h*factor));
                            result=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
                            result.eraseColor(Color.WHITE);
                            page.render(result,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        }
                    }
                }
            }catch(Exception ignored){}
            final Bitmap ready=result;
            post(()->{
                if (source==renderer && generation==documentGeneration) {
                    pagesBeingRendered.remove(i);
                    if(ready!=null){bitmapCache.put(i,ready);invalidate();}
                } else if(ready!=null && !ready.isRecycled())ready.recycle();
            });
        });
        return null;
    }
    public void release(){
        documentGeneration++;
        pageRenderWorker.shutdownNow();
        bitmapCache.evictAll();
        pagesBeingRendered.clear();
    }
    public Bitmap thumbnail(int i){if(renderer==null)return null;
        try { synchronized(renderer){try(PdfRenderer.Page page=renderer.openPage(i)){
            int w=240,h=Math.max(1,(int)(240f*page.getHeight()/page.getWidth()));
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return bitmap;
        }}}catch(Exception ignored){return null;}
    }
    public void drawForExport(Canvas canvas,int page,int w,int h){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Bitmap b=getBitmap(page);if(b!=null)canvas.drawBitmap(b,null,new RectF(0,0,w,h),p);
        else canvas.drawColor(Color.WHITE);
        if(annotations!=null)for(Annotations.Item a:annotations.onPage(page))a.draw(canvas,w,h,false);
    }
    public PointF getPageDimensions(int page){return pageDimensions.get(page);}
    private PointF toDocument(float x,float y){return new PointF((x+panX)/zoom,(y+panY)/zoom);}
    private int pageAt(PointF pt){for(int i=0;i<pageRects.size();i++)if(visiblePage(i)&&pageRects.get(i).contains(pt.x,pt.y))return i;return -1;}
    private PointF norm(int p,PointF pt){RectF r=pageRects.get(p);return new PointF(clamp((pt.x-r.left)/r.width(),0,1),clamp((pt.y-r.top)/r.height(),0,1));}
    private Annotations.Item nearest(int page,PointF pt,float radius){
        if(annotations==null)return null;
        Annotations.Item chosen=null;float best=radius;
        for(Annotations.Item a:annotations.onPage(page)){
            List<PointF> candidates=a.points();for(RectF box:a.boxes())candidates.add(new PointF(box.centerX(),box.centerY()));
            for(PointF v:candidates){float d=(float)Math.hypot(pt.x-v.x,pt.y-v.y);
                if(d<best){chosen=a;best=d;}}
        }return chosen;
    }
    private void eraseAt(int p,PointF q){Annotations.Item match=nearest(p,q,.045f);
        if(match!=null){annotations.remove(match);if(selected!=null&&selected.id().equals(match.id()))selected=null;
            if(listener!=null)listener.onAnnotationsChanged();invalidate();}}
    private void updateVisiblePage(){if(mode!=Mode.CONTINUOUS || renderer==null)return;
        float screenTop=panY/zoom + getHeight()/(zoom*3f);
        int found=currentPage;
        for(int i=0;i<pageRects.size();i++)if(pageRects.get(i).top<=screenTop)found=i;
        if(found!=currentPage){currentPage=found;if(annotations!=null)annotations.setLastPage(found);
            if(listener!=null)listener.onPageChanged(found);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        scaleDetector.onTouchEvent(e);
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_POINTER_DOWN){zooming=true;dragging=false;inProgress=null;return true;}
        if(action==MotionEvent.ACTION_POINTER_UP){zooming=true;return true;}
        if(e.getPointerCount()>1)return true;
        PointF doc=toDocument(e.getX(),e.getY());
        int page=pageAt(doc);
        boolean eraserButton=(e.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY)!=0;
        Tool activeTool=eraserButton?Tool.ERASER:tool;
        if(action==MotionEvent.ACTION_DOWN){
            zooming=false;dragging=true;prevX=e.getX();prevY=e.getY();
            activePage=page;inProgress=null;selectionPreview=null;
            if(page<0)return true;
            PointF pos=norm(page,doc);lastNormX=pos.x;lastNormY=pos.y;
            if(activeTool==Tool.ERASER){eraseAt(page,pos);return true;}
            if(activeTool==Tool.SELECT){selected=nearest(page,pos,.06f);invalidate();return true;}
            if(activeTool==Tool.TEXT){if(listener!=null)listener.onPlaceText(page,pos.x,pos.y);return true;}
            if(activeTool==Tool.EXACT){selectionPreview=new RectF(pos.x,pos.y,pos.x,pos.y);invalidate();return true;}
            if(activeTool!=Tool.PAN){
                float width=activeTool==Tool.HIGHLIGHT?markerWidth:penWidth;
                float opacity=activeTool==Tool.HIGHLIGHT?highlightOpacity:1;
                String type=activeTool==Tool.HIGHLIGHT?Annotations.HIGHLIGHT:activeTool==Tool.UNDERLINE?Annotations.UNDERLINE:activeTool==Tool.WAVE?Annotations.WAVE:Annotations.PEN;
                int c=activeTool==Tool.HIGHLIGHT?color:color;
                inProgress=Annotations.Item.newItem(page,type,c,width,opacity);inProgress.add(pos.x,pos.y);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(!dragging)return true;
            if(activeTool==Tool.PAN || (page<0 && activePage<0)){
                panX-=e.getX()-prevX;panY-=e.getY()-prevY;constrainScroll();updateVisiblePage();invalidate();
            }else if(activeTool==Tool.EXACT && selectionPreview!=null && activePage>=0){
                PointF q=norm(activePage,doc);
                selectionPreview.set(Math.min(lastNormX,q.x),Math.min(lastNormY,q.y),Math.max(lastNormX,q.x),Math.max(lastNormY,q.y));
                invalidate();
            }else if(activeTool==Tool.SELECT && selected!=null && activePage>=0){
                // Selection moves after the gesture is released; the original JSON isn't modified prematurely.
                invalidate();
            }else if(activeTool==Tool.ERASER && activePage>=0){eraseAt(activePage,norm(activePage,doc));}
            else if(inProgress!=null && activePage>=0){PointF q=norm(activePage,doc);inProgress.add(q.x,q.y);invalidate();}
            prevX=e.getX();prevY=e.getY();return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(!zooming && activeTool==Tool.SELECT && selected!=null && activePage>=0){
                PointF q=norm(activePage,doc);float dx=q.x-lastNormX,dy=q.y-lastNormY;
                if(Math.hypot(dx,dy)>.009){Annotations.Item moved=new Annotations.Item(copy(selected.json));moved.move(dx,dy);annotations.replace(moved);selected=moved;
                    if(listener!=null)listener.onAnnotationsChanged();}
                else if(listener!=null)listener.onSelection(selected);
            }
            if(!zooming && activeTool==Tool.EXACT && activePage>=0 && android.os.Build.VERSION.SDK_INT>=35){
                PointF stop=norm(activePage,doc);performTextSelection(activePage,lastNormX,lastNormY,stop.x,stop.y);
            }
            selectionPreview=null;
            if(!zooming && inProgress!=null && annotations!=null){
                if(inProgress.points().size()==1)inProgress.add(lastNormX+.0001f,lastNormY+.0001f);
                annotations.add(inProgress);
                if(listener!=null)listener.onAnnotationsChanged();
            }
            inProgress=null;dragging=false;invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){dragging=false;inProgress=null;invalidate();return true;}
        return true;
    }
    @android.annotation.TargetApi(35)
    private void performTextSelection(int page,float x1,float y1,float x2,float y2){
        PdfRenderer source=renderer;PointF dimension=pageDimensions.get(page);
        new Thread(()->{
            List<RectF> boxes=new ArrayList<>();StringBuilder text=new StringBuilder();
            try{
                synchronized(source){try(PdfRenderer.Page pdf=source.openPage(page)){
                    android.graphics.Point from=new android.graphics.Point(Math.round(x1*dimension.x),Math.round(y1*dimension.y));
                    android.graphics.Point to=new android.graphics.Point(Math.round(x2*dimension.x),Math.round(y2*dimension.y));
                    android.graphics.pdf.models.selection.PageSelection selection=pdf.selectContent(
                        new android.graphics.pdf.models.selection.SelectionBoundary(from),
                        new android.graphics.pdf.models.selection.SelectionBoundary(to));
                    if(selection!=null)for(android.graphics.pdf.content.PdfPageTextContent part:selection.getSelectedTextContents()){
                        if(text.length()>0)text.append(" ");text.append(part.getText());
                        for(RectF rect:part.getBounds())boxes.add(new RectF(rect.left/dimension.x,rect.top/dimension.y,rect.right/dimension.x,rect.bottom/dimension.y));
                    }
                }}
            }catch(Exception ignored){}
            post(()->{if(renderer==source&&listener!=null)listener.onTextSelected(page,boxes,text.toString());});
        },"KeeReads-TextSelect").start();
    }
    private static org.json.JSONObject copy(org.json.JSONObject x){try{return new org.json.JSONObject(x.toString());}catch(Exception ignored){return x;}}
}
