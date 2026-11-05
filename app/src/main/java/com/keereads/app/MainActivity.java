package com.keereads.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final int PICK_PDF=100, EXPORT_PDF=101;
    private static final int NAVY=0xFF17224B, INDIGO=0xFF4056CC, LIGHT=0xFFF5F7FC;
    private LinearLayout root, toolbarTop, toolbarBottom;
    private PdfSurface surface;
    private PdfRenderer renderer;
    private ParcelFileDescriptor openDescriptor;
    private Annotations notes;
    private Uri documentUri;
    private TextView fileTitle, pageNumber, status;
    private int[] palette={0xFFFFD64B,0xFFEF719B,0xFF78CAAA,0xFF7EB4F7,0xFFAC92E7,0xFFF49B58,0xFFEC5D5D,0xFF262D42};
    private int opacityPct=42;

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);setupUi();
        Uri inbound=getIntent()!=null?getIntent().getData():null;
        if(inbound!=null)openDocument(inbound);
        else {String last=getPreferences(MODE_PRIVATE).getString("last_pdf",null);
            if(last!=null)openDocument(Uri.parse(last));}
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,1);return t;
    }
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView toolButton(String title, Runnable click){
        TextView t=label(title,13,NAVY,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(9),dp(12),dp(9));
        t.setBackground(bg(0xFFF0F2F9,10));t.setContentDescription(title);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));lp.setMargins(dp(3),dp(4),dp(3),dp(4));
        t.setLayoutParams(lp);t.setOnClickListener(v->click.run());return t;
    }
    private void addButton(LinearLayout bar,String title,Runnable click){bar.addView(toolButton(title,click));}
    private LinearLayout scrollBar(){
        LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER_VERTICAL);b.setOrientation(LinearLayout.HORIZONTAL);b.setPadding(dp(6),0,dp(6),0);
        ScrollView unused=null;
        android.widget.HorizontalScrollView horizontal=new android.widget.HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);horizontal.setFillViewport(true);horizontal.addView(b);
        root.addView(horizontal,new LinearLayout.LayoutParams(-1,dp(49)));return b;
    }
    private void setupUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                root.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
            android.view.WindowInsetsController controller=getWindow().getInsetsController();
            if (controller!=null) {
                int lightBars=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars, lightBars);
            }
        }
        LinearLayout header=new LinearLayout(this);header.setBackgroundColor(NAVY);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),dp(6),dp(10),dp(6));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("Kee Reads",21,Color.WHITE,true);fileTitle=label("Offline PDF reader",11,0xFFCBD5FF,false);
        heading.addView(title);heading.addView(fileTitle);header.addView(heading,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView open=label("OPEN",12,Color.WHITE,true);open.setPadding(dp(12),dp(10),dp(12),dp(10));
        open.setOnClickListener(v->pickPdf());header.addView(open);
        TextView save=label("EXPORT",12,0xFFAFC2FF,true);save.setPadding(dp(12),dp(10),dp(12),dp(10));
        save.setOnClickListener(v->exportPrompt());header.addView(save);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(66)));

        toolbarTop=scrollBar();
        addButton(toolbarTop,"◀",()->surface.stepPage(-1));
        pageNumber=toolButton("— / —",()->gotoPageDialog());toolbarTop.addView(pageNumber);
        addButton(toolbarTop,"▶",()->surface.stepPage(1));
        addButton(toolbarTop,"Pages",this::showThumbnails);
        addButton(toolbarTop,"Find",this::searchPrompt);
        addButton(toolbarTop,"★",this::toggleBookmark);
        addButton(toolbarTop,"Marks",this::showBookmarks);
        addButton(toolbarTop,"Notes",this::showAnnotations);
        addButton(toolbarTop,"Cards",this::showFlashcards);
        addButton(toolbarTop,"Layout",this::cycleLayout);
        addButton(toolbarTop,"Theme",this::cycleTheme);
        addButton(toolbarTop,"−",()->surface.zoomBy(.8f));
        addButton(toolbarTop,"+",()->surface.zoomBy(1.25f));
        addButton(toolbarTop,"Fit",()->surface.resetZoom());

        FrameLayout frame=new FrameLayout(this);
        surface=new PdfSurface(this);surface.setListener(this);surface.setColor(palette[0]);
        frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        status=label("Pan · Use two fingers to zoom",11,0xFF3E4C79,true);
        status.setPadding(dp(12),dp(7),dp(12),dp(7));status.setBackground(bg(0xEEF0F4FC,14));
        FrameLayout.LayoutParams s=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);s.topMargin=dp(10);
        frame.addView(status,s);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));

        toolbarBottom=scrollBar();
        addButton(toolbarBottom,"Pan",()->chooseTool(PdfSurface.Tool.PAN));
        addButton(toolbarBottom,"Pen",()->chooseTool(PdfSurface.Tool.PEN));
        addButton(toolbarBottom,"Marker",()->chooseTool(PdfSurface.Tool.HIGHLIGHT));
        addButton(toolbarBottom,"Text select",()->chooseTool(PdfSurface.Tool.EXACT));
        addButton(toolbarBottom,"Line",()->chooseTool(PdfSurface.Tool.UNDERLINE));
        addButton(toolbarBottom,"Squiggle",()->chooseTool(PdfSurface.Tool.WAVE));
        addButton(toolbarBottom,"Erase",()->chooseTool(PdfSurface.Tool.ERASER));
        addButton(toolbarBottom,"Select",()->chooseTool(PdfSurface.Tool.SELECT));
        addButton(toolbarBottom,"Text",()->chooseTool(PdfSurface.Tool.TEXT));
        addButton(toolbarBottom,"Color",this::showColors);
        addButton(toolbarBottom,"Brush",this::showBrushSettings);
        addButton(toolbarBottom,"↶ Undo",()->{if(notes!=null){notes.undo();surface.deselect();surface.refresh();}});
        addButton(toolbarBottom,"↷ Redo",()->{if(notes!=null){notes.redo();surface.deselect();surface.refresh();}});
    }
    private void chooseTool(PdfSurface.Tool tool){
        if(tool==PdfSurface.Tool.EXACT && Build.VERSION.SDK_INT<35){toast("Exact text selection needs Android 15 or newer. Use Marker for freehand highlights.");return;}
        if(tool==PdfSurface.Tool.PEN && surface.getColor()==palette[0])surface.setColor(palette[7]);
        if(tool==PdfSurface.Tool.HIGHLIGHT && surface.getColor()==palette[7])surface.setColor(palette[0]);
        surface.setTool(tool);status.setText(tool.name().charAt(0)+tool.name().substring(1).toLowerCase()+" · "+(tool==PdfSurface.Tool.PAN?"Drag to scroll":tool==PdfSurface.Tool.SELECT?"Tap a mark to edit or drag it":"Touch page to annotate"));}
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
    private void toggleBookmark(){if(!requireDoc())return;int p=surface.getPage();notes.toggleBookmark(p);toast(notes.isBookmarked(p)?"Page bookmarked":"Bookmark removed");}
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
            if(which<palette.length){surface.setColor(palette[which]);toast("Color: "+labels[which]);}
            else{EditText t=new EditText(this);t.setHint("#2266DD");
                new AlertDialog.Builder(this).setTitle("Custom color").setView(t).setPositiveButton("Apply",(dd,w)->{
                    try{surface.setColor(Color.parseColor(t.getText().toString()));}catch(Exception e){toast("Invalid hex color");}
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
        PdfRenderer source=renderer;status.setText("Searching…");
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
                    if(renderer!=source)return;surface.setSearchMatches(found);status.setText("Search: "+total+" match(es)");
                    if(!found.isEmpty()){ArrayList<Integer> pages=new ArrayList<>(found.keySet());java.util.Collections.sort(pages);
                        String[] labels=new String[pages.size()];for(int i=0;i<pages.size();i++)labels[i]="Page "+(pages.get(i)+1)+" • "+found.get(pages.get(i)).size()+" highlighted bounds";
                        new AlertDialog.Builder(this).setTitle("Found “"+query+"”").setItems(labels,(dialog,which)->surface.setPage(pages.get(which)))
                            .setPositiveButton("Done",null).show();
                    }else toast("No matches");
                });
            }catch(Exception e){runOnUiThread(()->{if(renderer==source){status.setText("Search unavailable");toast("Search error: "+e.getMessage());}});}
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
        status.setText("Exporting PDF…");
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
                runOnUiThread(()->{status.setText("Export complete");toast("Annotated PDF saved");});
                } finally {
                    out.close();
                }
            }catch(Exception e){runOnUiThread(()->{status.setText("Export failed");toast("Export failed: "+e.getMessage());});}
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
