package com.keereads.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.content.res.Configuration;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;
import android.view.WindowManager;
import android.text.TextUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kee Reads: dependency-free native PDF reader and local annotation editor. */
public final class MainActivity extends Activity implements PdfSurface.Listener {
    private static final int PICK_PDF=100, EXPORT_PDF=101, SHEET_PANEL_ID=0x70001001;
    private static final int INK=0xFF20243A, MUTED=0xFF777F96, ACCENT=0xFF6257C8;
    private static final int CANVAS_BG=0xFFF2F3F8, PANEL=0xFFFFFFFF, PALE=0xFFF0EEFF, BORDER=0xFFE8E8F0;
    private LinearLayout root, colorTray, footerTabs;
    private FrameLayout readerFrame;
    private View emptyState, zoomControls;
    private final Map<PdfSurface.Tool,LinearLayout> toolTabs = new HashMap<>();
    private PdfSurface surface;
    private PdfRenderer renderer;
    private ParcelFileDescriptor openDescriptor;
    private Annotations notes;
    private Uri documentUri;
    private TextView fileTitle, fileSubtitle, pageNumber, status;
    private Glyph bookmarkGlyph;
    private int[] palette={0xFFFFD64B,0xFFEF719B,0xFF78CAAA,0xFF7EB4F7,0xFFAC92E7,0xFFF49B58,0xFFEC5D5D,0xFF262D42};
    private int opacityPct=42;

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(PANEL);
        getWindow().setNavigationBarColor(PANEL);
        if(Build.VERSION.SDK_INT>=26)getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else if(Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setupUi();
        Uri inbound=getIntent()!=null?getIntent().getData():null;
        if(inbound!=null)openDocument(inbound);
        else {String last=getPreferences(MODE_PRIVATE).getString("last_pdf",null);
            if(last!=null)openDocument(Uri.parse(last));}
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable borderBg(int color,int radius,int outline){
        GradientDrawable d=bg(color,radius);d.setStroke(dp(1),outline);return d;
    }
    private boolean compactUi(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    private LinearLayout horizontal(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);return c;}
    private LinearLayout vertical(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private View divider(){View v=new View(this);v.setBackgroundColor(BORDER);return v;}

    /** Vector icon drawn in app code: no system-emoji or external icon fonts needed. */
    private static final class Glyph extends View {
        private final String type;
        private int tint;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(Activity ctx,String type,int tint){super(ctx);this.type=type;this.tint=tint;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void tint(int color){tint=color;invalidate();}
        private void stroke(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
        private void circle(Canvas c,float x,float y,float r){c.drawCircle(x,y,r,p);}
        @Override protected void onDraw(Canvas raw){super.onDraw(raw);
            float scale=Math.min(getWidth(),getHeight())/26f;
            raw.save();raw.translate((getWidth()-26*scale)/2f,(getHeight()-26*scale)/2f);raw.scale(scale,scale);
            p.setColor(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(type){
                case "book":raw.drawRoundRect(4,6,12.5f,21,2,2,p);raw.drawRoundRect(13.5f,6,22,21,2,2,p);stroke(raw,13,7,13,20);break;
                case "folder":raw.drawRoundRect(3,7,23,21,2,2,p);stroke(raw,3,9,10,9);stroke(raw,4,7,10,4);stroke(raw,10,4,15,7);break;
                case "pen":stroke(raw,6,21,18,8);stroke(raw,18,8,21,11);stroke(raw,21,11,9,24);stroke(raw,6,21,9,24);stroke(raw,16,6,19,3);stroke(raw,19,3,23,7);stroke(raw,23,7,21,9);break;
                case "marker":stroke(raw,7,20,19,7);stroke(raw,17,5,21,9);stroke(raw,5,23,13,23);stroke(raw,8,17,13,22);break;
                case "text":p.setStrokeWidth(2.1f);stroke(raw,5,7,21,7);stroke(raw,13,7,13,21);stroke(raw,9,21,17,21);break;
                case "search":circle(raw,11.5f,11.5f,6.5f);stroke(raw,16.6f,16.6f,22,22);break;
                case "pages":raw.drawRoundRect(8,4,21,20,1.5f,1.5f,p);stroke(raw,5,9,5,22);stroke(raw,5,22,17,22);stroke(raw,11,9,17,9);stroke(raw,11,13,17,13);break;
                case "bookmark":{Path b=new Path();b.moveTo(7,4);b.lineTo(19,4);b.lineTo(19,23);b.lineTo(13,19);b.lineTo(7,23);b.close();raw.drawPath(b,p);break;}
                case "back":stroke(raw,16,5,8,13);stroke(raw,8,13,16,21);break;
                case "next":stroke(raw,10,5,18,13);stroke(raw,18,13,10,21);break;
                case "dots":p.setStyle(Paint.Style.FILL);circle(raw,6,13,1.65f);circle(raw,13,13,1.65f);circle(raw,20,13,1.65f);break;
                case "undo":raw.drawArc(6,6,22,22,210,290,false,p);stroke(raw,9,6,4,10);stroke(raw,4,10,11,13);break;
                case "redo":raw.drawArc(4,6,20,22,40,290,false,p);stroke(raw,17,6,22,10);stroke(raw,22,10,15,13);break;
                case "erase":{Path er=new Path();er.moveTo(6,16);er.lineTo(15,6);er.quadTo(17,4,19,6);er.lineTo(22,9);er.quadTo(23,11,21,13);er.lineTo(13,22);er.lineTo(8,22);er.close();raw.drawPath(er,p);stroke(raw,10,20,17,12);break;}
                case "underline":stroke(raw,7,4,7,14);raw.drawArc(7,6,19,18,0,180,false,p);stroke(raw,19,4,19,14);stroke(raw,5,22,21,22);break;
                case "wave":{Path wave=new Path();wave.moveTo(3,18);wave.cubicTo(6,10,10,25,13,18);wave.cubicTo(16,10,20,25,23,18);raw.drawPath(wave,p);stroke(raw,7,7,19,7);break;}
                case "select":raw.drawRoundRect(5,5,21,21,2,2,p);stroke(raw,9,10,16,10);stroke(raw,9,14,16,14);break;
                case "exact":stroke(raw,5,7,21,7);stroke(raw,5,13,18,13);stroke(raw,5,19,20,19);stroke(raw,4,3,4,23);break;
                case "notes":raw.drawRoundRect(5,3,21,23,2,2,p);stroke(raw,9,9,17,9);stroke(raw,9,14,17,14);stroke(raw,9,19,14,19);break;
                case "cards":raw.drawRoundRect(4,5,19,18,2,2,p);stroke(raw,9,21,21,21);stroke(raw,21,21,21,11);break;
                case "theme":raw.drawArc(5,3,21,23,65,245,false,p);raw.drawArc(3,3,20,23,290,130,false,p);break;
                case "layout":raw.drawRoundRect(3,5,23,21,2,2,p);stroke(raw,13,5,13,21);break;
                case "fit":raw.drawRoundRect(4,7,22,19,1.5f,1.5f,p);stroke(raw,8,4,8,7);stroke(raw,18,19,18,22);break;
                case "minus":stroke(raw,6,13,20,13);break;
                case "plus":stroke(raw,6,13,20,13);stroke(raw,13,6,13,20);break;
                case "color":{p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD64B);circle(raw,7,8,4);p.setColor(0xFFEF719B);circle(raw,19,8,4);p.setColor(0xFF78CAAA);circle(raw,13,19,4);break;}
                case "settings":circle(raw,13,13,4);raw.drawCircle(13,13,9,p);break;
                case "export":raw.drawRoundRect(5,11,21,23,2,2,p);stroke(raw,13,3,13,16);stroke(raw,8,11,13,16);stroke(raw,13,16,18,11);break;
                case "open":raw.drawRoundRect(4,9,22,22,2,2,p);stroke(raw,13,3,13,15);stroke(raw,9,7,13,3);stroke(raw,13,3,17,7);break;
                default:circle(raw,13,13,8);break;
            }
            raw.restore();
        }
    }
    private Glyph glyph(String id,int color,int size){Glyph v=new Glyph(this,id,color);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    private LinearLayout iconButton(String id,String description,Runnable task){
        LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setBackground(bg(PANEL,13));
        cell.addView(glyph(id,INK,23));cell.setContentDescription(description);
        cell.setOnClickListener(v->task.run());
        cell.setLayoutParams(new LinearLayout.LayoutParams(dp(43),dp(43)));
        return cell;
    }
    private void addIcon(LinearLayout bar,String id,String description,Runnable click){bar.addView(iconButton(id,description,click));}
    private void setupUi(){
        root=vertical();root.setBackgroundColor(PANEL);setContentView(root);
        if(Build.VERSION.SDK_INT>=35){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets sys=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                root.setPadding(sys.left,sys.top,sys.right,sys.bottom);return insets;
            });
            root.requestApplyInsets();
            android.view.WindowInsetsController ctrl=getWindow().getInsetsController();
            if(ctrl!=null){int light=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                |android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(light,light);}
        }
        // Document header: no overflowing toolbar; title and document controls stay fixed.
        LinearLayout header=horizontal();header.setPadding(dp(16),dp(7),dp(9),dp(7));header.setBackgroundColor(PANEL);
        FrameLayout logo=new FrameLayout(this);logo.setBackground(bg(PALE,14));
        TextView letter=label("K",23,ACCENT,true);letter.setGravity(Gravity.CENTER);
        logo.addView(letter,new FrameLayout.LayoutParams(-1,-1));
        header.addView(logo,new LinearLayout.LayoutParams(dp(45),dp(45)));
        LinearLayout heading=vertical();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(11),0,0,0);
        fileTitle=label("Kee Reads",17,INK,true);fileTitle.setSingleLine(true);fileTitle.setEllipsize(TextUtils.TruncateAt.END);
        fileSubtitle=label("A calmer way to read",11,MUTED,false);
        heading.addView(fileTitle);heading.addView(fileSubtitle);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(49),1));
        addIcon(header,"folder","Open PDF",this::pickPdf);
        addIcon(header,"dots","Document options",this::showAppMenu);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(compactUi()?56:65)));
        root.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));

        // One compact navigation rail for pages and reading utilities.
        LinearLayout nav=horizontal();nav.setPadding(dp(10),dp(3),dp(10),dp(3));nav.setBackgroundColor(PANEL);
        addIcon(nav,"back","Previous page",()->{if(requireDoc())surface.stepPage(-1);});
        pageNumber=label("— / —",12,INK,true);pageNumber.setGravity(Gravity.CENTER);
        pageNumber.setBackground(bg(CANVAS_BG,9));pageNumber.setPadding(dp(9),0,dp(9),0);
        pageNumber.setMinWidth(dp(70));pageNumber.setContentDescription("Go to page");pageNumber.setOnClickListener(v->gotoPageDialog());
        nav.addView(pageNumber,new LinearLayout.LayoutParams(-2,dp(33)));
        addIcon(nav,"next","Next page",()->{if(requireDoc())surface.stepPage(1);});
        View space=new View(this);nav.addView(space,new LinearLayout.LayoutParams(0,dp(1),1));
        addIcon(nav,"search","Search in PDF",this::searchPrompt);
        addIcon(nav,"pages","Page thumbnails",this::showThumbnails);
        LinearLayout mark=iconButton("bookmark","Bookmark this page",this::toggleBookmark);
        bookmarkGlyph=(Glyph)mark.getChildAt(0);nav.addView(mark);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(compactUi()?44:50)));
        root.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));

        readerFrame=new FrameLayout(this);readerFrame.setBackgroundColor(CANVAS_BG);
        surface=new PdfSurface(this);surface.setListener(this);surface.setColor(palette[0]);
        readerFrame.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        emptyState=createEmptyState();readerFrame.addView(emptyState,new FrameLayout.LayoutParams(-1,-1));
        status=label("",12,ACCENT,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(15),dp(8),dp(15),dp(8));
        status.setBackground(borderBg(PANEL,18,BORDER));status.setVisibility(View.GONE);
        FrameLayout.LayoutParams statusParams=new FrameLayout.LayoutParams(-2,dp(36),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        statusParams.topMargin=dp(14);readerFrame.addView(status,statusParams);
        zoomControls=createZoomControls();FrameLayout.LayoutParams zlp=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.END);
        zlp.setMargins(0,0,dp(14),dp(17));readerFrame.addView(zoomControls,zlp);
        root.addView(readerFrame,new LinearLayout.LayoutParams(-1,0,1));

        // Contextual color tray only appears while marking up a document.
        colorTray=horizontal();colorTray.setPadding(dp(12),dp(4),dp(12),dp(4));colorTray.setBackgroundColor(PANEL);
        root.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));
        root.addView(colorTray,new LinearLayout.LayoutParams(-1,dp(compactUi()?45:53)));

        // Five stable tool destinations; no sideways-scroll wall of tiny buttons.
        footerTabs=horizontal();footerTabs.setBackgroundColor(PANEL);footerTabs.setPadding(dp(6),dp(4),dp(6),dp(5));
        root.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));
        root.addView(footerTabs,new LinearLayout.LayoutParams(-1,dp(compactUi()?60:72)));
        addToolTab("book","Read",PdfSurface.Tool.PAN);
        addToolTab("marker","Highlight",PdfSurface.Tool.HIGHLIGHT);
        addToolTab("pen","Pen",PdfSurface.Tool.PEN);
        addToolTab("text","Text",PdfSurface.Tool.TEXT);
        addMoreTab();
        updateToolUi();updateDocumentUi();
    }
    private View createEmptyState(){
        final boolean compact=compactUi();
        LinearLayout content=vertical();content.setGravity(Gravity.CENTER);content.setBackgroundColor(CANVAS_BG);
        content.setPadding(dp(26),dp(20),dp(26),dp(20));
        FrameLayout art=new FrameLayout(this);art.setBackground(bg(PALE,30));
        Glyph book=glyph("book",ACCENT,68);FrameLayout.LayoutParams bi=new FrameLayout.LayoutParams(dp(68),dp(68),Gravity.CENTER);
        art.addView(book,bi);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(dp(130),dp(130));a.bottomMargin=dp(27);
        content.addView(art,a);
        TextView title=label("Your reading space",compact?20:25,INK,true);title.setGravity(Gravity.CENTER);
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView caption=label("Open a PDF to highlight, write notes,\nand keep everything together.",14,MUTED,false);
        caption.setGravity(Gravity.CENTER);caption.setLineSpacing(dp(4),1f);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(6);
        content.addView(caption,cp);if(compact)caption.setVisibility(View.GONE);
        TextView button=label("Open a PDF",15,Color.WHITE,true);button.setGravity(Gravity.CENTER);
        button.setBackground(bg(ACCENT,15));button.setOnClickListener(v->pickPdf());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(218),dp(compact?44:52));bp.topMargin=dp(compact?8:32);content.addView(button,bp);
        TextView privacy=label("Offline • Files stay on your device",11,MUTED,false);
        privacy.setGravity(Gravity.CENTER);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(25));pl.topMargin=dp(12);
        content.addView(privacy,pl);if(compact)privacy.setVisibility(View.GONE);return content;
    }
    private View createZoomControls(){
        LinearLayout zoom=horizontal();zoom.setPadding(dp(4),0,dp(4),0);zoom.setElevation(dp(4));
        zoom.setBackground(borderBg(PANEL,15,BORDER));
        addIcon(zoom,"minus","Zoom out",()->surface.zoomBy(.8f));
        TextView fit=label("Fit",12,ACCENT,true);fit.setGravity(Gravity.CENTER);fit.setOnClickListener(v->surface.resetZoom());
        zoom.addView(fit,new LinearLayout.LayoutParams(dp(42),dp(42)));
        addIcon(zoom,"plus","Zoom in",()->surface.zoomBy(1.25f));return zoom;
    }
    private void addToolTab(String icon,String title,PdfSurface.Tool selected){
        LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(4),dp(3),dp(4));
        Glyph g=glyph(icon,MUTED,25);tile.addView(g);
        TextView name=label(title,11,MUTED,true);name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(19));np.topMargin=dp(3);tile.addView(name,np);
        tile.setContentDescription(title+" tool");tile.setOnClickListener(v->chooseTool(selected));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);
        footerTabs.addView(tile,lp);toolTabs.put(selected,tile);
    }
    private void addMoreTab(){
        LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(4),dp(3),dp(4));
        tile.addView(glyph("dots",MUTED,25));
        TextView name=label("More",11,MUTED,true);name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(19));np.topMargin=dp(3);tile.addView(name,np);
        tile.setOnClickListener(v->showToolsSheet());tile.setContentDescription("More annotation tools");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);
        footerTabs.addView(tile,lp);
    }
    private void updateToolUi(){
        if(surface==null||colorTray==null)return;
        PdfSurface.Tool current=surface.getTool();
        for(Map.Entry<PdfSurface.Tool,LinearLayout> entry:toolTabs.entrySet()){
            boolean active=current==entry.getKey();LinearLayout tile=entry.getValue();
            tile.setBackground(bg(active?PALE:PANEL,16));
            ((Glyph)tile.getChildAt(0)).tint(active?ACCENT:MUTED);
            ((TextView)tile.getChildAt(1)).setTextColor(active?ACCENT:MUTED);
        }
        boolean colors=current==PdfSurface.Tool.PEN||current==PdfSurface.Tool.HIGHLIGHT
            ||current==PdfSurface.Tool.UNDERLINE||current==PdfSurface.Tool.WAVE
            ||current==PdfSurface.Tool.EXACT||current==PdfSurface.Tool.TEXT;
        colorTray.setVisibility(colors?View.VISIBLE:View.GONE);
        if(colors)rebuildColorTray();
    }
    private void rebuildColorTray(){
        if(colorTray==null)return;
        colorTray.removeAllViews();
        TextView label=label("COLOR",10,MUTED,true);label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(dp(52),dp(37));colorTray.addView(label,title);
        for(int i=0;i<6;i++){
            final int color=palette[i];FrameLayout slot=new FrameLayout(this);
            View dot=new View(this);GradientDrawable shape=bg(color,100);
            if(surface.getColor()==color)shape.setStroke(dp(3),PANEL);else shape.setStroke(dp(1),BORDER);
            dot.setBackground(shape);FrameLayout.LayoutParams dotp=new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER);
            slot.addView(dot,dotp);
            if(surface.getColor()==color)slot.setBackground(borderBg(PALE,12,ACCENT));
            else slot.setBackground(bg(PANEL,12));
            slot.setContentDescription("Select color "+(i+1));slot.setOnClickListener(v->{surface.setColor(color);rebuildColorTray();});
            colorTray.addView(slot,new LinearLayout.LayoutParams(0,dp(38),1));
        }
        LinearLayout extra=iconButton("color","More colors",this::showColors);colorTray.addView(extra);
        LinearLayout width=iconButton("settings","Brush width and opacity",this::showBrushSettings);colorTray.addView(width);
    }
    private void updateDocumentUi(){
        boolean has=renderer!=null;
        if(emptyState!=null)emptyState.setVisibility(has?View.GONE:View.VISIBLE);
        if(zoomControls!=null)zoomControls.setVisibility(has?View.VISIBLE:View.GONE);
        if(fileSubtitle!=null)fileSubtitle.setText(has?surface.pageCount()+" pages  •  Saved locally":"A calmer way to read");
        updateBookmarkUi();
    }
    private void updateBookmarkUi(){
        if(bookmarkGlyph!=null)bookmarkGlyph.tint(notes!=null&&notes.isBookmarked(surface.getPage())?ACCENT:INK);
    }
    private void showStatus(String message){
        if(status==null)return;status.setText(message);status.setVisibility(message==null||message.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void chooseTool(PdfSurface.Tool tool){
        if(tool==PdfSurface.Tool.EXACT&&Build.VERSION.SDK_INT<35){
            toast("Select text requires Android 15+. Marker works on scanned PDFs.");return;
        }
        if(tool==PdfSurface.Tool.PEN && surface.getColor()==palette[0])surface.setColor(palette[7]);
        if(tool==PdfSurface.Tool.HIGHLIGHT && surface.getColor()==palette[7])surface.setColor(palette[0]);
        surface.setTool(tool);showStatus("");updateToolUi();
    }
    private Dialog showSheet(String title){
        Dialog dialog=new Dialog(this);
        LinearLayout panel=vertical();panel.setPadding(dp(20),dp(14),dp(20),dp(24));panel.setBackground(bg(PANEL,25));
        View handle=new View(this);handle.setBackground(bg(BORDER,4));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(5));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(13);panel.addView(handle,hp);
        TextView heading=label(title,19,INK,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(40));panel.addView(heading,tp);
        panel.setId(SHEET_PANEL_ID);dialog.setContentView(panel);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=w.getAttributes();params.dimAmount=.32f;w.setAttributes(params);}
        return dialog;
    }
    private void sheetSection(LinearLayout panel,String name){
        TextView header=label(name.toUpperCase(),11,MUTED,true);header.setLetterSpacing(.12f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(38));lp.topMargin=dp(6);panel.addView(header,lp);
    }
    private void sheetItem(GridLayout grid,Dialog dialog,String icon,String title,Runnable callback){
        LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(4),dp(8),dp(4),dp(8));
        tile.setBackground(bg(CANVAS_BG,13));tile.addView(glyph(icon,ACCENT,24));
        TextView name=label(title,11,INK,true);name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(36));np.topMargin=dp(5);tile.addView(name,np);
        tile.setContentDescription(title);tile.setOnClickListener(v->{dialog.dismiss();callback.run();});
        GridLayout.LayoutParams cell=new GridLayout.LayoutParams();cell.width=0;cell.height=dp(86);
        cell.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);
        cell.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(tile,cell);
    }
    private GridLayout sheetGrid(LinearLayout panel){
        GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        panel.addView(grid,new LinearLayout.LayoutParams(-1,-2));return grid;
    }
    private void showToolsSheet(){
        Dialog d=showSheet("Tools & annotations");
        LinearLayout panel=d.findViewById(SHEET_PANEL_ID);
        sheetSection(panel,"Make a mark");GridLayout g=sheetGrid(panel);
        sheetItem(g,d,"underline","Underline",()->chooseTool(PdfSurface.Tool.UNDERLINE));
        sheetItem(g,d,"wave","Squiggle",()->chooseTool(PdfSurface.Tool.WAVE));
        sheetItem(g,d,"erase","Eraser",()->chooseTool(PdfSurface.Tool.ERASER));
        sheetItem(g,d,"select","Select mark",()->chooseTool(PdfSurface.Tool.SELECT));
        sheetItem(g,d,"exact","Select text",()->chooseTool(PdfSurface.Tool.EXACT));
        sheetItem(g,d,"color","Colors",this::showColors);
        sheetItem(g,d,"settings","Brush",this::showBrushSettings);
        sheetItem(g,d,"notes","All notes",this::showAnnotations);
        sheetSection(panel,"Recent changes");GridLayout actions=sheetGrid(panel);
        sheetItem(actions,d,"undo","Undo",()->{if(notes!=null){notes.undo();surface.deselect();surface.refresh();}});
        sheetItem(actions,d,"redo","Redo",()->{if(notes!=null){notes.redo();surface.deselect();surface.refresh();}});
        sheetItem(actions,d,"cards","Flashcards",this::showFlashcards);
        sheetItem(actions,d,"bookmark","Bookmarks",this::showBookmarks);
    }
    private void showAppMenu(){
        Dialog d=showSheet("Reader options");
        LinearLayout panel=d.findViewById(SHEET_PANEL_ID);
        sheetSection(panel,"File");GridLayout files=sheetGrid(panel);
        sheetItem(files,d,"open","Open PDF",this::pickPdf);
        sheetItem(files,d,"export","Export PDF",this::exportPrompt);
        sheetItem(files,d,"pages","Pages",this::showThumbnails);
        sheetItem(files,d,"search","Search",this::searchPrompt);
        sheetSection(panel,"Reading");GridLayout reading=sheetGrid(panel);
        sheetItem(reading,d,"theme","Theme",this::cycleTheme);
        sheetItem(reading,d,"layout","Page layout",this::cycleLayout);
        sheetItem(reading,d,"fit","Reset zoom",()->surface.resetZoom());
        sheetItem(reading,d,"bookmark","Bookmarks",this::showBookmarks);
    }
    private void pickPdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_PDF);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        if(request==PICK_PDF){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            openDocument(uri);
        } else if(request==EXPORT_PDF){writePdf(data.getData());}
    }
    private String getDisplayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst()){
                String name=c.getString(0);
                if(name!=null && !name.trim().isEmpty())return name;
            }
        }catch(Exception ignored){}
        return "Document.pdf";
    }
    private void openDocument(Uri uri){
        ParcelFileDescriptor descriptor=null;
        PdfRenderer newRenderer=null;
        try{
            descriptor=getContentResolver().openFileDescriptor(uri,"r");
            if(descriptor==null)throw new Exception("Document unavailable");
            newRenderer=new PdfRenderer(descriptor);
            if(renderer!=null){
                surface.open(null,null);
                synchronized(renderer){renderer.close();}
            }
            if(openDescriptor!=null)try{openDescriptor.close();}catch(Exception ignored){}
            renderer=newRenderer;openDescriptor=descriptor;documentUri=uri;notes=new Annotations(this,uri.toString());
            surface.open(renderer,notes);
            fileTitle.setText(getDisplayName(uri));
            updateDocumentUi();showStatus("");
            getPreferences(MODE_PRIVATE).edit().putString("last_pdf",uri.toString()).apply();
            int page=Math.max(0,Math.min(notes.getLastPage(),renderer.getPageCount()-1));
            surface.post(()->surface.setPage(page));onPageChanged(page);
        }catch(Exception ex){
            if(newRenderer!=null && newRenderer!=renderer){
                try{newRenderer.close();}catch(Exception ignored){}
            }
            if(descriptor!=null && descriptor!=openDescriptor){
                try{descriptor.close();}catch(Exception ignored){}
            }
            toast("Couldn't open PDF: "+ex.getMessage());
            getPreferences(MODE_PRIVATE).edit().remove("last_pdf").apply();
        }
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean requireDoc(){if(renderer==null){toast("Open a PDF first");return false;}return true;}
    @Override public void onPageChanged(int page){pageNumber.setText((page+1)+" / "+Math.max(1,surface.pageCount()));
        updateBookmarkUi();
        if(notes!=null)notes.setLastPage(page);
    }
    @Override public void onAnnotationsChanged(){surface.refresh();}
    @Override public void onPlaceText(int page,float x,float y){
        EditText input=new EditText(this);input.setHint("Write your note here");input.setSingleLine(false);input.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("Add text box").setView(input)
            .setPositiveButton("Insert",(d,w)->{if(notes==null)return;
                String s=input.getText().toString().trim();if(s.isEmpty())return;
                Annotations.Item item=Annotations.Item.newItem(page,Annotations.TEXT,surface.getColor(),.004f,1);
                item.add(x,y);item.setText(s);notes.add(item);surface.refresh();})
            .setNegativeButton("Cancel",null).show();
    }
    @Override public void onSelection(Annotations.Item item){
        if(item==null)return;
        String[] choices={"Change to current color","Edit text (text boxes)","Delete annotation","Cancel"};
        new AlertDialog.Builder(this).setTitle("Annotation on page "+(item.page()+1))
            .setItems(choices,(dialog,which)->{
                if(which==0){Annotations.Item edited=copy(item);edited.setColor(surface.getColor());notes.replace(edited);surface.refresh();}
                else if(which==1){if(!Annotations.TEXT.equals(item.type())){toast("Only text boxes can be edited");return;}
                    EditText field=new EditText(this);field.setText(item.text());
                    new AlertDialog.Builder(this).setTitle("Edit text").setView(field).setPositiveButton("Save",(dd,ww)->{
                        Annotations.Item changed=copy(item);changed.setText(field.getText().toString());notes.replace(changed);surface.refresh();
                    }).setNegativeButton("Cancel",null).show();}
                else if(which==2){notes.remove(item);surface.deselect();surface.refresh();}
            }).show();
    }
    private Annotations.Item copy(Annotations.Item item){try{return new Annotations.Item(new org.json.JSONObject(item.json.toString()));}catch(Exception e){return item;}}
    private void gotoPageDialog(){if(!requireDoc())return;
        EditText input=new EditText(this);input.setInputType(2);input.setHint("1 to "+renderer.getPageCount());
        new AlertDialog.Builder(this).setTitle("Go to page").setView(input).setPositiveButton("Go",(d,w)->{
            try{surface.setPage(Integer.parseInt(input.getText().toString())-1);}catch(Exception ignored){}
        }).setNegativeButton("Cancel",null).show();
    }
    private void toggleBookmark(){if(!requireDoc())return;int p=surface.getPage();notes.toggleBookmark(p);updateBookmarkUi();toast(notes.isBookmarked(p)?"Page bookmarked":"Bookmark removed");}
    private void showBookmarks(){if(!requireDoc())return;
        ArrayList<Integer> pages=new ArrayList<>(notes.bookmarks());java.util.Collections.sort(pages);
        if(pages.isEmpty()){toast("No bookmarked pages");return;}
        String[] names=new String[pages.size()];for(int i=0;i<pages.size();i++)names[i]="★ Page "+(pages.get(i)+1);
        new AlertDialog.Builder(this).setTitle("Bookmarks").setItems(names,(d,i)->surface.setPage(pages.get(i))).setNegativeButton("Close",null).show();
    }
    @Override public void onTextSelected(int page,List<RectF> boxes,String selectedText){
        if(boxes.isEmpty()||selectedText.trim().isEmpty()){toast("No selectable text here. Use Marker on scanned pages.");return;}
        final String clipped=selectedText.length()>160?selectedText.substring(0,160)+"…":selectedText;
        final String[] actions={"Highlight selected text","Underline selected text","Squiggly underline","Copy selected text","Define selected text","Make flashcard"};
        new AlertDialog.Builder(this).setTitle("Selected: "+clipped).setItems(actions,(d,which)->{
            if(which<=2){
                String type=which==0?Annotations.EXACT_HIGHLIGHT:which==1?Annotations.EXACT_UNDERLINE:Annotations.EXACT_WAVE;
                Annotations.Item item=Annotations.Item.newItem(page,type,surface.getColor(),.003f,which==0?opacityPct/100f:1);
                item.setText(selectedText);for(RectF r:boxes)item.addBox(r);
                notes.add(item);surface.refresh();
            }else if(which==3){android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                c.setPrimaryClip(android.content.ClipData.newPlainText("PDF excerpt",selectedText));toast("Copied excerpt");
            }else if(which==4){
                Intent intent=new Intent(Intent.ACTION_WEB_SEARCH);intent.putExtra(android.app.SearchManager.QUERY,"define "+selectedText);
                try{startActivity(intent);}catch(Exception ignored){toast("Install a browser or dictionary app to look this up");}
            }else if(which==5){
                EditText answer=new EditText(this);answer.setHint("Answer / explanation");answer.setSingleLine(false);answer.setMinLines(2);
                new AlertDialog.Builder(this).setTitle("Flashcard question: "+clipped)
                    .setView(answer).setPositiveButton("Create",(dialog,w)->{
                        Annotations.Item card=Annotations.Item.newItem(page,Annotations.FLASHCARD,Color.BLACK,.003f,1);
                        card.setText(selectedText);card.setAnswer(answer.getText().toString().trim());notes.add(card);toast("Flashcard saved");
                    }).setNegativeButton("Cancel",null).show();
            }
        }).setNegativeButton("Cancel",null).show();
    }
    private void showFlashcards(){if(!requireDoc())return;
        List<Annotations.Item> cards=new ArrayList<>();for(Annotations.Item item:notes.all())if(Annotations.FLASHCARD.equals(item.type()))cards.add(item);
        if(cards.isEmpty()){toast("Select PDF text and choose Make flashcard to create cards");return;}
        String[] titles=new String[cards.size()];for(int i=0;i<cards.size();i++){
            String front=cards.get(i).text();titles[i]=(i+1)+". "+(front.length()>70?front.substring(0,70)+"…":front);
        }
        new AlertDialog.Builder(this).setTitle("Study cards ("+cards.size()+")")
            .setItems(titles,(dialog,index)->{
                Annotations.Item card=cards.get(index);
                new AlertDialog.Builder(this).setTitle("Card "+(index+1)+" — Page "+(card.page()+1))
                    .setMessage(card.text()).setPositiveButton("Reveal answer",(d,w)->{
                        new AlertDialog.Builder(this).setTitle("Answer").setMessage(card.answer().isEmpty()?"No answer saved":card.answer())
                            .setPositiveButton("Go to page",(dd,ww)->surface.setPage(card.page())).setNegativeButton("Close",null).show();
                    }).setNeutralButton("Delete",(d,w)->{notes.remove(card);toast("Card removed");})
                    .setNegativeButton("Close",null).show();
            }).setNegativeButton("Close",null).show();
    }
    private void showAnnotations(){if(!requireDoc())return;
        List<Annotations.Item> items=notes.all();items.removeIf(item->Annotations.FLASHCARD.equals(item.type()));items.sort(Comparator.comparingInt(Annotations.Item::page).thenComparingLong(Annotations.Item::created));
        if(items.isEmpty()){toast("No annotations yet");return;}
        String[] labels=new String[items.size()];for(int i=0;i<items.size();i++){
            Annotations.Item a=items.get(i);labels[i]="Page "+(a.page()+1)+"  •  "+a.type()+(a.text().isEmpty()?"":"  —  "+a.text().replace('\n',' '));}
        new AlertDialog.Builder(this).setTitle("Your annotations ("+items.size()+")")
            .setItems(labels,(d,i)->surface.setPage(items.get(i).page())).setNegativeButton("Close",null).show();
    }
    private void showThumbnails(){if(!requireDoc())return;
        final GridView grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(dp(9));grid.setHorizontalSpacing(dp(9));grid.setPadding(dp(12),dp(12),dp(12),dp(12));
        grid.setAdapter(new BaseAdapter(){
            @Override public int getCount(){return surface.pageCount();}
            @Override public Object getItem(int i){return i;}
            @Override public long getItemId(int i){return i;}
            @Override public View getView(int i,View previous,ViewGroup parent){
                LinearLayout layout=new LinearLayout(MainActivity.this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);
                ImageView iv=new ImageView(MainActivity.this);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Bitmap bitmap=surface.thumbnail(i);if(bitmap!=null)iv.setImageBitmap(bitmap);
                layout.addView(iv,new LinearLayout.LayoutParams(-1,dp(140)));
                TextView caption=label(""+(i+1),12,NAVY,true);caption.setGravity(Gravity.CENTER);
                layout.addView(caption,new LinearLayout.LayoutParams(-1,dp(25)));return layout;
            }
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pages").setView(grid).setNegativeButton("Close",null).create();
        grid.setOnItemClickListener((p,v,index,id)->{surface.setPage(index);dialog.dismiss();});dialog.show();
    }
    private void cycleLayout(){PdfSurface.Mode m=surface.getMode();
        m=m==PdfSurface.Mode.CONTINUOUS?PdfSurface.Mode.SINGLE:m==PdfSurface.Mode.SINGLE?PdfSurface.Mode.SPREAD:PdfSurface.Mode.CONTINUOUS;
        surface.setMode(m);toast("Layout: "+m.name().toLowerCase());
    }
    private void cycleTheme(){PdfSurface.Theme t=surface.getTheme();
        t=t==PdfSurface.Theme.WHITE?PdfSurface.Theme.SEPIA:t==PdfSurface.Theme.SEPIA?PdfSurface.Theme.DARK:PdfSurface.Theme.WHITE;
        surface.setTheme(t);toast("Theme: "+t.name().toLowerCase());
    }
    private void showColors(){
        final String[] labels={"Yellow","Pink","Green","Blue","Purple","Orange","Red","Ink","Custom hex…"};
        new AlertDialog.Builder(this).setTitle("Highlight / pen color").setItems(labels,(d,which)->{
            if(which<palette.length){surface.setColor(palette[which]);rebuildColorTray();toast("Color: "+labels[which]);}
            else{EditText t=new EditText(this);t.setHint("#2266DD");
                new AlertDialog.Builder(this).setTitle("Custom color").setView(t).setPositiveButton("Apply",(dd,w)->{
                    try{surface.setColor(Color.parseColor(t.getText().toString()));rebuildColorTray();}catch(Exception e){toast("Invalid hex color");}
                }).setNegativeButton("Cancel",null).show();}
        }).show();
    }
    private void showBrushSettings(){
        LinearLayout layout=new LinearLayout(this);layout.setPadding(dp(22),dp(14),dp(22),dp(8));layout.setOrientation(LinearLayout.VERTICAL);
        TextView pen=label("Pen thickness",14,NAVY,true);layout.addView(pen);
        SeekBar width=new SeekBar(this);width.setMax(100);width.setProgress(25);layout.addView(width);
        TextView opacity=label("Marker opacity",14,NAVY,true);layout.addView(opacity);
        SeekBar alpha=new SeekBar(this);alpha.setMax(90);alpha.setProgress(opacityPct);layout.addView(alpha);
        new AlertDialog.Builder(this).setTitle("Brush settings").setView(layout).setPositiveButton("Apply",(d,w)->{
            surface.setWidth(.001f+.014f*width.getProgress()/100f);
            opacityPct=Math.max(10,alpha.getProgress());surface.setOpacity(opacityPct/100f);
        }).setNegativeButton("Cancel",null).show();
    }
    private void searchPrompt(){if(!requireDoc())return;
        if(Build.VERSION.SDK_INT<35){new AlertDialog.Builder(this).setTitle("Search requires Android 15+")
            .setMessage("This build uses the system PDF text-search API available on Android 15 and newer. Search inside scanned image-only PDFs needs OCR, which is not yet implemented.")
            .setPositiveButton("OK",null).show();return;}
        EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Search document text");
        new AlertDialog.Builder(this).setTitle("Find in PDF").setView(field).setPositiveButton("Find",(d,w)->runSearch(field.getText().toString().trim()))
            .setNeutralButton("Clear",(d,w)->surface.clearSearch()).setNegativeButton("Cancel",null).show();
    }
    @android.annotation.TargetApi(35) private void runSearch(String query){
        if(query.isEmpty()||renderer==null)return;
        PdfRenderer source=renderer;showStatus("Searching…");
        new Thread(()->{
            final Map<Integer,List<RectF>> found=new HashMap<>();int count=0;
            try{
                for(int i=0;i<source.getPageCount();i++){
                    synchronized(source){try(PdfRenderer.Page p=source.openPage(i)){
                        List<android.graphics.pdf.models.PageMatchBounds> matches=p.searchText(query);
                        if(matches!=null && !matches.isEmpty()){
                            List<RectF> bounds=new ArrayList<>();for(android.graphics.pdf.models.PageMatchBounds match:matches)bounds.addAll(match.getBounds());
                            found.put(i,bounds);count+=matches.size();
                        }
                    }}
                }
                final int total=count;
                runOnUiThread(()->{
                    if(renderer!=source)return;surface.setSearchMatches(found);showStatus("Search: "+total+" match(es)");
                    if(!found.isEmpty()){ArrayList<Integer> pages=new ArrayList<>(found.keySet());java.util.Collections.sort(pages);
                        String[] labels=new String[pages.size()];for(int i=0;i<pages.size();i++)labels[i]="Page "+(pages.get(i)+1)+" • "+found.get(pages.get(i)).size()+" highlighted bounds";
                        new AlertDialog.Builder(this).setTitle("Found “"+query+"”").setItems(labels,(dialog,which)->surface.setPage(pages.get(which)))
                            .setPositiveButton("Done",null).show();
                    }else toast("No matches");
                });
            }catch(Exception e){runOnUiThread(()->{if(renderer==source){showStatus("Search unavailable");toast("Search error: "+e.getMessage());}});}
        },"KeeReads-PdfSearch").start();
    }
    private void exportPrompt(){if(!requireDoc())return;
        new AlertDialog.Builder(this).setTitle("Export annotated PDF")
            .setMessage("Export creates a new flattened PDF. Highlights, ink and text boxes stay visible in any reader, but exported pages become images, so original text selection and search are lost. Your original PDF is never modified.")
            .setPositiveButton("Choose destination",(d,w)->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");
                String filename=getDisplayName(documentUri);i.putExtra(Intent.EXTRA_TITLE,filename.replaceAll("(?i)\\.pdf$","")+" - Kee Reads.pdf");
                startActivityForResult(i,EXPORT_PDF);
            }).setNegativeButton("Cancel",null).show();
    }
    private void writePdf(Uri dest){
        if(renderer==null||documentUri==null)return;
        final Uri source=documentUri;
        final List<Annotations.Item> snapshot=new ArrayList<>();
        if(notes!=null)for(Annotations.Item item:notes.all())snapshot.add(copy(item));
        showStatus("Exporting PDF…");
        new Thread(()->{
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(source,"r");
                PdfRenderer reader=new PdfRenderer(fd)){
                PdfDocument out=new PdfDocument();
                try {
                for(int i=0;i<reader.getPageCount();i++){
                    try(PdfRenderer.Page src=reader.openPage(i)){
                        int w=Math.max(1,src.getWidth()),h=Math.max(1,src.getHeight());
                        PdfDocument.Page page=out.startPage(new PdfDocument.PageInfo.Builder(w,h,i+1).create());
                        Canvas cv=page.getCanvas();cv.drawColor(Color.WHITE);
                        float scale=Math.min(2f,1650f/w);
                        scale=Math.min(scale,(float)Math.sqrt(3000000d/((double)w*h)));
                        int rw=Math.max(1,Math.round(w*scale));
                        int rh=Math.max(1,Math.round(h*scale));
                        Bitmap bitmap=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
                        try {
                            bitmap.eraseColor(Color.WHITE);
                            src.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                            cv.drawBitmap(bitmap,null,new RectF(0,0,w,h),paint);
                        } finally { bitmap.recycle(); }
                        for(Annotations.Item item:snapshot)if(item.page()==i)item.draw(cv,w,h,false);
                        out.finishPage(page);
                    }
                }
                try(OutputStream output=getContentResolver().openOutputStream(dest,"w")){
                    if(output==null)throw new Exception("Cannot open destination");out.writeTo(output);
                }
                runOnUiThread(()->{showStatus("Export complete");toast("Annotated PDF saved");});
                } finally {
                    out.close();
                }
            }catch(Exception e){runOnUiThread(()->{showStatus("Export failed");toast("Export failed: "+e.getMessage());});}
        },"KeeReads-Export").start();
    }
    @Override protected void onDestroy(){
        if(renderer!=null){
            surface.open(null,null);
            synchronized(renderer){renderer.close();}
            renderer=null;
        }
        if(openDescriptor!=null)try{openDescriptor.close();}catch(Exception ignored){}
        surface.release();
        super.onDestroy();
    }
}
