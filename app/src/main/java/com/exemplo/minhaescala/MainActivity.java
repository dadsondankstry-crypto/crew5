package com.exemplo.minhaescala;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Interface nativa. Nenhum arquivo e enviado a servidores. */
public class MainActivity extends Activity {
    private static final int REQ_IMAGES = 101;
    private static final int REQ_EXPORT = 102;
    private static final int REQ_BACKUP_OUT = 103;
    private static final int REQ_BACKUP_IN = 104;
    private static final int BG=Color.rgb(9,22,39), PANEL=Color.rgb(18,39,59), BORDER=Color.rgb(41,68,84);
    private static final int WHITE=Color.rgb(237,246,249), MUTED=Color.rgb(155,181,191), ACCENT=Color.rgb(53,221,193);
    private static final int ORANGE=Color.rgb(255,190,102);
    private LocalStore store;
    private TextRecognizer recognizer;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService searchExecutor=Executors.newSingleThreadExecutor();
    private volatile int searchVersion=0;
    private boolean hasSearch=false;
    private final ArrayDeque<Uri> importQueue=new ArrayDeque<>();
    private LinearLayout screen, resultArea, photosArea, favoritesArea;
    private EditText searchInput;
    private TextView summaryText, statusText, resultsTitle;
    private Button todayButton, favoriteButton;
    private boolean todayOnly=false, onlyFavorites=false, processing=false;
    private String exportContent="";
    private int importIndex=0;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        store=new LocalStore(this);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // Reclassifica fotos antigas da v1: a palavra 'TRAINING' aparece frequentemente
        // na COLUNA de observacoes das escalas e nao define o tipo do documento.
        boolean migrated=false;
        for(LocalStore.Photo p:store.photos){
            String fixed=ScheduleParser.kind(p.raw);
            if(!p.kindManual&&!fixed.equals(p.kind)){p.kind=fixed;migrated=true;}
        }
        if(migrated)store.save();
        buildUi();renderAll();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private void addGap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView text(String title,int size,int color,boolean strong){
        TextView v=new TextView(this);v.setText(title);v.setTextColor(color);v.setTextSize(size);
        if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setLineSpacing(dp(2),1.04f);return v;
    }
    private Button button(String title,boolean solid){
        Button b=new Button(this);b.setText(title);b.setTextColor(solid?BG:WHITE);
        b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(bg(solid?ACCENT:PANEL,solid?0:BORDER,13));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setPadding(dp(11),dp(4),dp(11),dp(4));return b;
    }
    private LinearLayout card(LinearLayout into) {
        LinearLayout box=col();box.setBackground(bg(PANEL,BORDER,17));pad(box,15,15);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        into.addView(box,p);return box;
    }
    private void pairButtons(LinearLayout target,Button left,Button right){
        LinearLayout r=row();LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1);
        a.rightMargin=dp(7);r.addView(left,a);r.addView(right,new LinearLayout.LayoutParams(0,dp(48),1));target.addView(r);
    }
    private void section(LinearLayout into,String title){TextView label=text(title,19,WHITE,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);p.bottomMargin=dp(10);into.addView(label,p);}
    private void buildUi() {
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        // Android 15/16: conteúdo não deve ficar atrás do relógio/barra de navegação.
        if(Build.VERSION.SDK_INT>=35){
            sc.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                sc.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        screen=col();pad(screen,17,19);sc.addView(screen);setContentView(sc);
        TextView tag=text("●  LEITURA LOCAL  •  SEM INTERNET",10,ACCENT,true);screen.addView(tag);
        addGap(screen,7);screen.addView(text("Minha Escala",30,WHITE,true));
        TextView sub=text("Encontre seu turno e treinamentos nas fotos da escala.",13,MUTED,false);screen.addView(sub);addGap(screen,14);
        LinearLayout start=card(screen);
        summaryText=text("",14,WHITE,true);start.addView(summaryText);addGap(start,5);
        start.addView(text("Suas fotos ficam neste celular. O reconhecimento de texto funciona offline.",12,MUTED,false));addGap(start,12);
        Button importBtn=button("＋  Importar fotos",true);
        Button manualBtn=button("＋  Registrar horário",false);
        pairButtons(start,importBtn,manualBtn);
        importBtn.setOnClickListener(v->pickImages());manualBtn.setOnClickListener(v->editShift(null,null));
        addGap(start,11);
        statusText=text("Pronto para importar imagens.",12,ACCENT,false);start.addView(statusText);
        section(screen,"Pesquisar por pessoa");
        searchInput=new EditText(this);
        searchInput.setSingleLine(true);searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);searchInput.setHint("Digite nome ou sobrenome...");
        searchInput.setBackground(bg(PANEL,BORDER,14));pad(searchInput,13,12);
        searchInput.setTextSize(16);screen.addView(searchInput,new LinearLayout.LayoutParams(-1,dp(53)));
        // Nao processa fotos nem redesenha resultados durante a digitacao.
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){showResults();return true;}
            return false;
        });
        addGap(screen,9);
        Button searchBtn=button("🔍  Pesquisar",true),clearSearch=button("Limpar pesquisa",false);
        pairButtons(screen,searchBtn,clearSearch);
        searchBtn.setOnClickListener(v->showResults());
        clearSearch.setOnClickListener(v->{searchInput.setText("");onlyFavorites=false;
            updateFilterButtons();searchVersion++;hasSearch=false;showSearchHint();});
        addGap(screen,9);
        LinearLayout filter=row();todayButton=button("Todos os dias",true);
        favoriteButton=button("☆ Favoritos",false);pairButtons(filter,todayButton,favoriteButton);
        screen.addView(filter);
        todayButton.setOnClickListener(v->{todayOnly=!todayOnly;updateFilterButtons();showResults();});
        favoriteButton.setOnClickListener(v->{onlyFavorites=!onlyFavorites;updateFilterButtons();showResults();});
        Button saveFavorite=button("☆  Favoritar nome pesquisado",false);
        LinearLayout.LayoutParams sf=new LinearLayout.LayoutParams(-1,dp(44));sf.topMargin=dp(9);
        screen.addView(saveFavorite,sf);saveFavorite.setOnClickListener(v->favoriteQuery());
        favoritesArea=col();screen.addView(favoritesArea);
        section(screen,"Resultados");
        resultsTitle=text("",12,MUTED,false);screen.addView(resultsTitle);addGap(screen,7);
        resultArea=col();screen.addView(resultArea);
        section(screen,"Fotos importadas");
        photosArea=col();screen.addView(photosArea);
        LinearLayout tools=card(screen);tools.addView(text("Ferramentas",15,WHITE,true));addGap(tools,9);
        Button export=button("⇩  Exportar pesquisa em CSV",false);tools.addView(export);
        export.setOnClickListener(v->exportResults());addGap(tools,8);
        Button backupOut=button("📦  Salvar backup ZIP (inclui fotos)",false);
        tools.addView(backupOut);backupOut.setOnClickListener(v->requestBackupExport());addGap(tools,8);
        Button backupIn=button("📥  Restaurar backup ZIP",false);
        tools.addView(backupIn);backupIn.setOnClickListener(v->requestBackupImport());addGap(tools,8);
        Button privacy=button("ⓘ  Como funciona / privacidade",false);tools.addView(privacy);
        privacy.setOnClickListener(v->showInfo());addGap(tools,8);
        Button clear=button("Apagar todos os dados deste aplicativo",false);tools.addView(clear);
        clear.setOnClickListener(v->clearEverything());
        addGap(screen,18);screen.addView(text("Versão 2.0 • Busca só ao tocar Pesquisar • OCR sempre sujeito a conferência.",11,MUTED,false));
    }
    private void renderAll(){
        int scales=0,trainings=0,others=0;
        for(LocalStore.Photo p:store.photos){
            if("ESCALA".equals(p.kind))scales++;
            else if("TREINAMENTO".equals(p.kind))trainings++;
            else others++;
        }
        summaryText.setText(store.photos.size()+" foto(s): "+scales+" escala(s), "+trainings+" treino(s), "+others+" outro(s)\n"+
            store.shifts.size()+" ficha(s) confirmada(s)");
        updateFilterButtons();showFavorites();showPhotos();
        if(!hasSearch)showSearchHint();
    }
    private void updateFilterButtons(){
        todayButton.setText(todayOnly?"📅  Só hoje":"📅  Todas as datas");
        todayButton.setBackground(bg(todayOnly?ACCENT:PANEL,todayOnly?0:BORDER,12));
        todayButton.setTextColor(todayOnly?BG:WHITE);
        favoriteButton.setText(onlyFavorites?"★  Favoritos":"☆  Favoritos");
        favoriteButton.setBackground(bg(onlyFavorites?ACCENT:PANEL,onlyFavorites?0:BORDER,12));
        favoriteButton.setTextColor(onlyFavorites?BG:WHITE);
    }
    private void favoriteQuery(){
        String q=searchInput.getText().toString().trim();
        if(q.length()<2){toast("Digite um nome para favoritar.");return;}
        if(store.favorites.contains(q))store.favorites.remove(q);else store.favorites.add(q);
        store.save();showFavorites();showResults();
    }
    private void showFavorites(){
        favoritesArea.removeAllViews();
        if(store.favorites.isEmpty())return;
        addGap(favoritesArea,9);favoritesArea.addView(text("Toque num favorito para pesquisar:",12,MUTED,false));
        for(String name:store.favorites){
            Button b=button("★  "+name,false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.topMargin=dp(5);
            favoritesArea.addView(b,p);b.setOnClickListener(v->{searchInput.setText(name);searchInput.setSelection(searchInput.length());onlyFavorites=false;updateFilterButtons();showResults();});
            b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Remover favorito?")
                .setMessage(name).setNegativeButton("Cancelar",null)
                .setPositiveButton("Remover",(d,w)->{store.favorites.remove(name);store.save();showFavorites();showResults();}).show();return true;});
        }
    }
    private boolean isVisibleDate(String date){return !todayOnly||ScheduleParser.today().equals(date);}
    private boolean queryMatchesFavorite(String text){
        if(!onlyFavorites)return true;
        for(String f:store.favorites)if(ScheduleParser.matches(text,f))return true;
        return false;
    }
    private void showSearchHint(){
        if(resultArea==null)return;
        resultArea.removeAllViews();
        resultsTitle.setText("Digite o nome ou MSC ID completo. Toque 🔍 Pesquisar; a busca não acontece enquanto você digita.");
        LinearLayout c=card(resultArea);
        c.addView(text("Pesquisa pronta",15,WHITE,true));
        c.addView(text("Escalas, cadastros e treinamentos são identificados separadamente. Horários OCR nunca são confirmados automaticamente.",12,MUTED,false));
    }
    private static final class HitGroup {
        String name="",mscId="",date="",type="";
        final ArrayList<ScheduleParser.Hit> hits=new ArrayList<>();
    }
    private static final class SearchOutput {
        final ArrayList<LocalStore.Shift> confirmed=new ArrayList<>();
        final LinkedHashMap<String,HitGroup> grouped=new LinkedHashMap<>();
        boolean truncated=false;
    }
    /** A busca completa roda FORA da thread de interface, somente apos clique/Enter. */
    private void showResults(){
        if(resultArea==null||searchInput==null)return;
        final String query=searchInput.getText().toString().trim();
        if(query.isEmpty()&&!onlyFavorites){hasSearch=false;showSearchHint();return;}
        if(onlyFavorites&&store.favorites.isEmpty()){
            toast("Favorite algum nome primeiro.");return;
        }
        hasSearch=true;
        final int generation=++searchVersion;
        final boolean filterToday=todayOnly,filterFavorites=onlyFavorites;
        final ArrayList<LocalStore.Photo> photos=new ArrayList<>(store.photos);
        final ArrayList<LocalStore.Shift> saved=new ArrayList<>(store.shifts);
        final ArrayList<String> favs=new ArrayList<>(store.favorites);
        resultsTitle.setText("🔍 Pesquisando nas "+photos.size()+" fotos salvas…");
        resultArea.removeAllViews();
        // Fecha o teclado ao iniciar, sem alterar o texto da pesquisa.
        try {InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
             if(imm!=null)imm.hideSoftInputFromWindow(searchInput.getWindowToken(),0);}
        catch(Exception ignored){}
        searchExecutor.execute(()->{
            if(generation!=searchVersion)return;
            SearchOutput output=new SearchOutput();
            ArrayList<LocalStore.Shift> reversed=new ArrayList<>(saved);
            Collections.reverse(reversed);
            for(LocalStore.Shift shift:reversed){
                if(filterToday&&!ScheduleParser.today().equals(shift.date))continue;
                if(filterFavorites&&!matchesAny(shift.person,favs))continue;
                if(!query.isEmpty()&&!ScheduleParser.matches(shift.person+" "+shift.mscId+" "+shift.section+" "+shift.notes,query))continue;
                output.confirmed.add(shift);
                if(output.confirmed.size()>=35){output.truncated=true;break;}
            }
            int checked=0;
            for(int i=photos.size()-1;i>=0;i--){
                if(generation!=searchVersion)return;
                LocalStore.Photo p=photos.get(i);
                if(filterToday&&!ScheduleParser.today().equals(p.date))continue;
                ArrayList<ScheduleParser.Hit> matches=new ArrayList<>();
                if(!query.isEmpty())matches.addAll(ScheduleParser.search(p,query));
                else for(String favorite:favs)matches.addAll(ScheduleParser.search(p,favorite));
                for(ScheduleParser.Hit h:matches){
                    if(filterFavorites&&!matchesAny(h.person,favs))continue;
                    // Com data desconhecida, nao mistura resultados de fotos distintas.
                    String who=!h.mscId.isEmpty()?"id:"+h.mscId:"nome:"+ScheduleParser.normalize(h.person);
                    // Para escala sem data, agrupa achados da MESMA pessoa,
                    // mas AVISA que as fotos podem pertencer a dias distintos.
                    // Treinamentos diferentes nunca sao fundidos em uma so sessao.
                    String session="TREINAMENTO".equals(p.kind)?"|"+h.times+"|"+h.section:"";
                    String key=p.kind+"|"+p.date+"|"+who+session;
                    HitGroup g=output.grouped.get(key);
                    if(g==null){
                        if(output.grouped.size()>=70){output.truncated=true;break;}
                        g=new HitGroup();g.name=h.person;g.mscId=h.mscId;g.date=p.date;
                        g.type=p.kind;output.grouped.put(key,g);
                    }
                    if(g.hits.size()<8)g.hits.add(h);
                }
                checked++;
                if(output.truncated)break;
            }
            runOnUiThread(()->{
                if(generation!=searchVersion||isFinishing())return;
                resultArea.removeAllViews();
                int count=output.confirmed.size()+output.grouped.size();
                resultsTitle.setText(count+" ficha(s) organizada(s) • "+photos.size()+" foto(s) na biblioteca"+
                    (output.truncated?" • parte dos resultados (refine a busca)":""));
                if(count==0){
                    LinearLayout c=card(resultArea);c.addView(text("Nenhum registro localizado.",15,WHITE,true));
                    c.addView(text("Verifique a grafia, desative Só hoje ou confirme a data da foto. Procure também pelo MSC ID.",12,MUTED,false));
                    return;
                }
                for(LocalStore.Shift shift:output.confirmed)showManualResult(shift);
                ArrayList<HitGroup> groups=new ArrayList<>(output.grouped.values());
                addGroupBatch(groups,0,generation);
            });
        });
    }
    private static boolean matchesAny(String person,ArrayList<String> names){
        for(String name:names)if(ScheduleParser.matches(person,name))return true;
        return false;
    }
    /** Renderiza ate 10 fichas por vez para nao congelar a interface. */
    private void addGroupBatch(ArrayList<HitGroup> groups,int from,int generation){
        if(generation!=searchVersion)return;
        int until=Math.min(from+10,groups.size());
        for(int i=from;i<until;i++)showGroup(groups.get(i));
        if(until<groups.size())resultArea.postDelayed(()->addGroupBatch(groups,until,generation),16);
    }
    private void showGroup(HitGroup group){
        if(group.hits.isEmpty())return;
        ScheduleParser.Hit primary=group.hits.get(0);
        LocalStore.Photo photo=primary.photo;
        LinkedHashMap<String,LocalStore.Photo> sources=new LinkedHashMap<>();
        for(ScheduleParser.Hit hit:group.hits)sources.put(hit.photo.id,hit.photo);
        LinearLayout c=card(resultArea);
        String type="TREINAMENTO".equals(group.type)?"📚 TREINAMENTO":
                    "ESCALA".equals(group.type)?"📅 ESCALA":
                    "CADASTRO".equals(group.type)?"🪪 CADASTRO":"📄 "+group.type;
        c.addView(text("✦ OCR • "+type+" • conferir",11,ACCENT,true));addGap(c,5);
        c.addView(text(group.name,18,WHITE,true));
        if(!group.mscId.isEmpty())c.addView(text("ID: "+group.mscId,12,MUTED,false));
        c.addView(text(group.date.isEmpty()?"Data não confirmada — as fotos podem ser de DIAS DIFERENTES":"Data do documento: "+group.date,13,ORANGE,true));
        addGap(c,4);c.addView(text(primary.times,13,WHITE,true));
        if(!primary.section.isEmpty())c.addView(text("Setor/local: "+primary.section,12,MUTED,false));
        c.addView(text(primary.description,11,ORANGE,false));
        addGap(c,6);
        c.addView(text("Fonte: "+sources.size()+" foto(s) · "+group.hits.size()+" linha(s) OCR semelhante(s)",11,MUTED,false));
        c.addView(text("Trecho: "+shorten(primary.context,230),11,MUTED,false));
        addGap(c,9);
        Button open=button(sources.size()>1?"▧  Ver fotos":"▧  Ver foto",false);
        Button confirm=button("✓  Corrigir / confirmar",true);
        pairButtons(c,open,confirm);
        open.setOnClickListener(v->{
            ArrayList<LocalStore.Photo> options=new ArrayList<>(sources.values());
            if(options.size()==1){viewPhoto(options.get(0));return;}
            String[] names=new String[options.size()];
            for(int i=0;i<names.length;i++)names[i]=options.get(i).title;
            new AlertDialog.Builder(this).setTitle("Fotos de origem")
                .setItems(names,(d,which)->viewPhoto(options.get(which))).show();
        });
        confirm.setOnClickListener(v->{
            LocalStore.Shift s=new LocalStore.Shift();s.person=group.name;s.mscId=group.mscId;
            s.date=group.date;s.section=primary.section;
            // NAO preenche entrada/saida com horas de outra coluna por engano.
            if("TREINAMENTO".equals(group.type))s.training=primary.times+" • "+primary.section;
            s.notes=primary.times+". "+shorten(primary.context,260);
            s.source=photo.title;
            editShift(null,s);
        });
    }
    private static String shorten(String s,int max){
        if(s==null)return "";return s.length()<=max?s:s.substring(0,max)+"…";
    }
    private void showManualResult(LocalStore.Shift s){
        LinearLayout c=card(resultArea);
        c.addView(text("✓ FICHA CORRIGIDA / SALVA",11,ACCENT,true));addGap(c,5);
        c.addView(text(s.person,18,WHITE,true));
        if(!s.mscId.isEmpty())c.addView(text("ID: "+s.mscId,12,MUTED,false));
        c.addView(text(s.date.isEmpty()?"Data não informada":"Data: "+s.date,13,ORANGE,true));
        if(!s.start.isEmpty()||!s.end.isEmpty())c.addView(text("☀ Manhã: "+s.start+" — "+s.end,14,WHITE,true));
        if(!s.start2.isEmpty()||!s.end2.isEmpty())c.addView(text("🌙 Tarde: "+s.start2+" — "+s.end2,14,WHITE,true));
        if(!s.break1.isEmpty())c.addView(text("☕ Pausa 1: "+s.break1,12,MUTED,false));
        if(!s.break2.isEmpty())c.addView(text("☕ Pausa 2: "+s.break2,12,MUTED,false));
        if(!s.training.isEmpty())c.addView(text("📚 Treinamento: "+s.training,12,ORANGE,false));
        if(!s.section.isEmpty())c.addView(text("🏢 Setor: "+s.section,12,WHITE,false));
        if(!s.notes.isEmpty())c.addView(text("Observações: "+s.notes,11,MUTED,false));
        addGap(c,8);Button edit=button("✎  Editar / excluir",false);c.addView(edit);
        edit.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Ficha salva")
            .setItems(new String[]{"Editar","Excluir"},(d,which)->{
                if(which==0)editShift(s,null);else new AlertDialog.Builder(this)
                    .setTitle("Excluir ficha?").setMessage(s.person).setNegativeButton("Cancelar",null)
                    .setPositiveButton("Excluir",(di,w)->{store.shifts.remove(s);store.save();renderAll();if(hasSearch)showResults();}).show();
            }).show());
    }
    private void showPhotos(){
        photosArea.removeAllViews();
        if(store.photos.isEmpty()){
            LinearLayout card=card(photosArea);card.addView(text("Nenhuma foto importada ainda.",14,MUTED,false));return;
        }
        int count=0;
        for(int i=store.photos.size()-1;i>=0;i--){
            if(count++>=24)break;
            LocalStore.Photo photo=store.photos.get(i);
            LinearLayout c=card(photosArea);
            c.addView(text("▧  "+photo.title,15,WHITE,true));
            c.addView(text(photo.kind+"  •  "+(photo.date.isEmpty()?"Data a confirmar":photo.date)+"  •  "+photo.lines.size()+" linhas OCR",12,MUTED,false));
            addGap(c,9);Button open=button("Ver foto",false),editDate=button("Definir data",false);
            pairButtons(c,open,editDate);
            open.setOnClickListener(v->viewPhoto(photo));editDate.setOnClickListener(v->setPhotoDate(photo));
            addGap(c,7);Button organized=button("▤  Ver texto organizado (OCR)",true);c.addView(organized);
            organized.setOnClickListener(v->showOrganizedPhoto(photo));
            addGap(c,7);Button manage=button("✎ OCR / tipo / corrigir / excluir",false);c.addView(manage);
            manage.setOnClickListener(v->managePhoto(photo));
        }
        if(store.photos.size()>24){
            LinearLayout note=card(photosArea);
            note.addView(text("Mostrando as 24 fotos mais recentes de "+store.photos.size()+". As demais continuam pesquisáveis.",12,MUTED,false));
        }
    }
    private void showOrganizedPhoto(LocalStore.Photo p) {
        LinearLayout list=col();pad(list,12,12);
        list.addView(text(p.kind+"  •  "+(p.date.isEmpty()?"Data nao identificada":p.date),17,Color.BLACK,true));
        list.addView(text("Tabela estimada pelo OCR. Confirme os nomes e as colunas na foto original antes de usar o horario.",12,Color.DKGRAY,false));
        addGap(list,8);
        ArrayList<LocalStore.Line> lines=new ArrayList<>(p.lines);
        Collections.sort(lines,(a,b)->a.cy()==b.cy()?Integer.compare(a.left,b.left):Integer.compare(a.cy(),b.cy()));
        ArrayList<ArrayList<LocalStore.Line>> groups=new ArrayList<>();
        for(LocalStore.Line l:lines) {
            ArrayList<LocalStore.Line> group=null;
            for(int k=groups.size()-1;k>=0&&k>=groups.size()-3;k--){
                ArrayList<LocalStore.Line> g=groups.get(k);
                if(Math.abs(g.get(0).cy()-l.cy())<=12){group=g;break;}
            }
            if(group==null){group=new ArrayList<>();groups.add(group);}group.add(l);
        }
        int shown=0;
        for(ArrayList<LocalStore.Line> group:groups){
            Collections.sort(group,(a,b)->Integer.compare(a.left,b.left));
            StringBuilder b=new StringBuilder();
            for(LocalStore.Line l:group){if(b.length()>0)b.append("     |     ");b.append(l.text);}
            String rowText=b.toString().trim();
            if(rowText.isEmpty())continue;
            // Mostra as linhas, sem fingir que reconstruiu exatamente as colunas.
            TextView line=text(rowText,13,Color.rgb(19,47,62),false);
            line.setBackground(bg(Color.rgb(238,247,249),Color.rgb(203,224,226),9));pad(line,9,8);
            LinearLayout.LayoutParams par=new LinearLayout.LayoutParams(-1,-2);par.bottomMargin=dp(5);
            list.addView(line,par);shown++;if(shown>=180)break;
        }
        if(shown==0)list.addView(text("Nenhuma linha identificada. Tente foto mais nítida.",13,Color.DKGRAY,false));
        ScrollView sc=new ScrollView(this);sc.addView(list);
        new AlertDialog.Builder(this).setTitle("Linhas reorganizadas · OCR")
            .setView(sc).setPositiveButton("Fechar",null).show();
    }
    private void setPhotoDate(LocalStore.Photo p){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(p.date);
        e.setHint("dd/MM/aaaa (vazio se desconhecida)");
        new AlertDialog.Builder(this).setTitle("Data da escala")
            .setMessage("Informe a data que aparece no cabeçalho, não a data de embarque.")
            .setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{
                String raw=e.getText().toString().trim();String parsed=raw.isEmpty()?"":ScheduleParser.normalizeDate(raw);
                if(!raw.isEmpty()&&parsed.isEmpty()){toast("Data inválida. Use dd/MM/aaaa.");return;}
                p.date=parsed;store.save();renderAll();if(hasSearch)showResults();
            }).show();
    }
    private void managePhoto(LocalStore.Photo p){
        new AlertDialog.Builder(this).setTitle(p.title)
            .setItems(new String[]{"Ver OCR completo","Corrigir texto para pesquisa","Classificar foto (escala, treinamento, aviso...)","Analisar foto novamente","Excluir foto do aplicativo"},(d,which)->{
                if(which==0)showLongText("Texto reconhecido",p.raw);
                else if(which==1)editCorrection(p);
                else if(which==2)changeKind(p);
                else if(which==3){status("Reanalisando "+p.title+"...");recognize(p,null);}
                else new AlertDialog.Builder(this).setTitle("Excluir foto e OCR?")
                    .setMessage("A foto original da galeria NÃO será apagada. Apenas a cópia local do app.")
                    .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(di,w)->{
                        new File(getFilesDir(),p.file).delete();store.photos.remove(p);store.save();renderAll();if(hasSearch)showResults();
                    }).show();
            }).show();
    }
    private void changeKind(LocalStore.Photo p){
        String[] kinds={"ESCALA", "TREINAMENTO", "CADASTRO", "CABINES", "AVISO"};
        new AlertDialog.Builder(this).setTitle("Tipo de documento")
            .setMessage("Se o OCR classificou errado, escolha o tipo manualmente. Treinamento não é turno de trabalho.")
            .setItems(kinds,(di,which)->{
                p.kind=kinds[which];p.kindManual=true;store.save();renderAll();if(hasSearch)showResults();
            }).setNegativeButton("Cancelar",null).show();
    }
    private void editCorrection(LocalStore.Photo p){
        EditText e=new EditText(this);e.setMinLines(6);e.setMaxLines(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setText(p.corrected.isEmpty()?p.raw:p.corrected);
        ScrollView sc=new ScrollView(this);sc.addView(e);
        new AlertDialog.Builder(this).setTitle("Texto corrigido para pesquisa")
            .setMessage("Não substitui a foto. Corrija nomes e linhas mal reconhecidos; esta versão não reconstrói automaticamente as colunas.")
            .setView(sc).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",(d,w)->{p.corrected=e.getText().toString();store.save();renderAll();}).show();
    }
    private void showLongText(String title,String content){
        TextView tv=text(content.isEmpty()?"Nenhum texto reconhecido.":content,13,Color.BLACK,false);
        tv.setTextIsSelectable(true);pad(tv,16,15);
        ScrollView s=new ScrollView(this);s.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("Fechar",null).show();
    }
    private void viewPhoto(LocalStore.Photo p){
        try {
            File f=new File(getFilesDir(),p.file);if(!f.isFile()){toast("Foto não encontrada.");return;}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
            int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>2300)sample*=2;
            o.inJustDecodeBounds=false;o.inSampleSize=sample;
            Bitmap bmp=BitmapFactory.decodeFile(f.getPath(),o);
            if(bmp==null){toast("Não foi possível abrir esta imagem.");return;}
            ImageView img=new ImageView(this);img.setImageBitmap(bmp);img.setAdjustViewBounds(true);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);img.setBackgroundColor(Color.rgb(12,19,29));
            ScrollView s=new ScrollView(this);s.addView(img,new ScrollView.LayoutParams(-1,-2));
            Dialog d=new AlertDialog.Builder(this).setTitle(p.title).setView(s)
                .setPositiveButton("Fechar",(dia,w)->bmp.recycle()).create();
            d.setOnCancelListener(di->bmp.recycle());d.show();
        }catch(Exception e){toast("Erro ao visualizar: "+e.getMessage());}
    }
    private EditText input(String hint,String initial){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);
        e.setText(initial==null?"":initial);return e;
    }
    private void editShift(LocalStore.Shift editing,LocalStore.Shift initial){
        LocalStore.Shift ref=editing!=null?editing:(initial!=null?initial:new LocalStore.Shift());
        LinearLayout fields=col();pad(fields,18,6);
        fields.addView(text("Conferir horários em cada coluna da foto. Não preenchemos as horas automaticamente.",12,Color.DKGRAY,false));
        EditText name=input("Nome completo",ref.person),msc=input("ID MSC (opcional)",ref.mscId),date=input("Data dd/MM/aaaa",ref.date);
        EditText start=input("Manhã: entrada (07:00)",ref.start),end=input("Manhã: saída (12:00)",ref.end);
        EditText start2=input("Tarde: entrada (13:00)",ref.start2),end2=input("Tarde: saída (18:00)",ref.end2);
        EditText pause1=input("Pausa 1 (08:00–08:30)",ref.break1),pause2=input("Pausa 2 (14:00–14:30)",ref.break2);
        EditText training=input("Treinamento (horário e local)",ref.training);
        EditText section=input("Setor / restaurante / local",ref.section),note=input("Observações",ref.notes);
        fields.addView(name);fields.addView(msc);fields.addView(date);
        fields.addView(start);fields.addView(end);fields.addView(start2);fields.addView(end2);
        fields.addView(pause1);fields.addView(pause2);fields.addView(training);
        fields.addView(section);fields.addView(note);
        ScrollView sc=new ScrollView(this);sc.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing!=null?"Editar ficha confirmada":"Conferir / registrar ficha")
            .setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            String person=name.getText().toString().trim(),rawDate=date.getText().toString().trim();
            String parsed=rawDate.isEmpty()?"":ScheduleParser.normalizeDate(rawDate);
            if(person.length()<2){name.setError("Informe um nome.");return;}
            if(!rawDate.isEmpty()&&parsed.isEmpty()){date.setError("Use dd/MM/aaaa.");return;}
            EditText[] hourFields={start,end,start2,end2};
            for(EditText field:hourFields){String v2=field.getText().toString().trim();
                if(!v2.isEmpty()&&!v2.matches("([01]\\d|2[0-3]):[0-5]\\d")){
                    field.setError("Use HH:MM, por exemplo 07:00");return;
                }
            }
            LocalStore.Shift out=editing==null?new LocalStore.Shift():editing;
            if(editing==null)out.id="s"+System.nanoTime();
            out.person=person;out.mscId=msc.getText().toString().trim();out.date=parsed;
            out.start=start.getText().toString().trim();out.end=end.getText().toString().trim();
            out.start2=start2.getText().toString().trim();out.end2=end2.getText().toString().trim();
            out.break1=pause1.getText().toString().trim();out.break2=pause2.getText().toString().trim();
            out.training=training.getText().toString().trim();
            out.section=section.getText().toString().trim();out.notes=note.getText().toString().trim();
            out.source=ref.source;
            if(editing==null)store.shifts.add(out);
            if(!store.save())toast("Falha ao salvar dados; verifique armazenamento.");
            dialog.dismiss();renderAll();if(hasSearch)showResults();
        }));
        dialog.show();
    }
    private void pickImages(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{startActivityForResult(i,REQ_IMAGES);}catch(Exception ex){toast("Seletor de imagens não disponível.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==REQ_IMAGES){
            if(data.getClipData()!=null){
                for(int j=0;j<data.getClipData().getItemCount();j++)importQueue.add(data.getClipData().getItemAt(j).getUri());
            }else if(data.getData()!=null)importQueue.add(data.getData());
            if(!processing)importNext();
        }else if(request==REQ_BACKUP_OUT&&data.getData()!=null){
            writeBackup(data.getData());
        }else if(request==REQ_BACKUP_IN&&data.getData()!=null){
            readBackup(data.getData());
        }else if(request==REQ_EXPORT&&data.getData()!=null){
            Uri uri=data.getData();String payload=exportContent;
            executor.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new Exception("Destino inválido");out.write(payload.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->toast("Relatório CSV salvo."));
            }catch(Exception ex){runOnUiThread(()->toast("Falha ao exportar: "+ex.getMessage()));}});
        }
    }
    private void requestBackupExport(){
        if(processing){toast("Conclua a importação de fotos primeiro.");return;}
        new AlertDialog.Builder(this).setTitle("Backup privado")
            .setMessage("O ZIP inclui imagens, nomes, identificações e horários de colegas. Guarde somente em local privado. Não envie para repositórios públicos.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Criar backup",(di,w)->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
                i.putExtra(Intent.EXTRA_TITLE,"minha_escala_backup.zip");
                startActivityForResult(i,REQ_BACKUP_OUT);
            }).show();
    }
    private void requestBackupImport(){
        if(processing){toast("Conclua a importação de fotos primeiro.");return;}
        new AlertDialog.Builder(this).setTitle("Restaurar backup")
            .setMessage("Restaurar um backup SUBSTITUI os registros deste aplicativo, inclusive horários confirmados. Os arquivos originais de sua galeria não são alterados.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Escolher ZIP",(di,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
                startActivityForResult(i,REQ_BACKUP_IN);
            }).show();
    }
    private static void zipCopy(InputStream in,OutputStream out,long max) throws Exception{
        byte[] buf=new byte[32768];long count=0;int n;
        while((n=in.read(buf))!=-1){count+=n;if(count>max)throw new Exception("Limite de tamanho do backup ultrapassado");out.write(buf,0,n);}
    }
    private void writeBackup(Uri uri){
        final ArrayList<LocalStore.Photo> photos=new ArrayList<>(store.photos);
        final ArrayList<LocalStore.Shift> shifts=new ArrayList<>(store.shifts);
        final ArrayList<String> favs=new ArrayList<>(store.favorites);
        status("Criando backup privado…");
        executor.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new Exception("Não foi possível abrir destino");
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out))){
                    JSONObject root=new JSONObject();root.put("schema",2);
                    JSONArray p=new JSONArray(),sh=new JSONArray(),fav=new JSONArray();
                    for(LocalStore.Photo item:photos)p.put(item.toJson());
                    for(LocalStore.Shift item:shifts)sh.put(item.toJson());
                    for(String f:favs)fav.put(f);
                    root.put("photos",p);root.put("shifts",sh);root.put("favorites",fav);
                    zip.putNextEntry(new ZipEntry("database.json"));
                    zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                    for(LocalStore.Photo item:photos){
                        File photo=new File(getFilesDir(),item.file);
                        if(!photo.isFile())throw new Exception("Imagem não encontrada para backup: "+item.title);
                        zip.putNextEntry(new ZipEntry("photos/"+item.file));
                        try(FileInputStream src=new FileInputStream(photo)){zipCopy(src,zip,35_000_000);}
                        zip.closeEntry();
                    }
                }
                runOnUiThread(()->status("Backup ZIP concluído: "+photos.size()+" fotos no índice."));
            }catch(Exception e){runOnUiThread(()->{
                toast("Erro no backup: "+e.getMessage());status("Backup não concluído.");
            });}
        });
    }
    private void readBackup(Uri uri){
        status("Verificando backup ZIP…");
        executor.execute(()->{
            File temp=new File(getCacheDir(),"restaurar_"+System.nanoTime());
            temp.mkdirs();
            try{
                byte[] metadata=null;int entries=0;long total=0;
                try(InputStream original=getContentResolver().openInputStream(uri)){
                    if(original==null)throw new Exception("Arquivo não disponível");
                    try(ZipInputStream zip=new ZipInputStream(original)){
                        ZipEntry entry;
                        while((entry=zip.getNextEntry())!=null){
                            if(++entries>1500)throw new Exception("Muitas entradas no ZIP");
                            String name=entry.getName();
                            if(entry.isDirectory()){zip.closeEntry();continue;}
                            if("database.json".equals(name)){
                                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                                zipCopy(zip,buf,15_000_000);metadata=buf.toByteArray();
                                total+=metadata.length;
                            }else if(name.matches("photos/escala_[0-9_]+\\.(jpg|jpeg|png|webp|heic|bmp)")){
                                File dst=new File(temp,name.substring(7));
                                try(FileOutputStream out=new FileOutputStream(dst)){zipCopy(zip,out,35_000_000);}
                                total+=dst.length();
                            }
                            if(total>750_000_000)throw new Exception("Backup acima do limite de segurança");
                            zip.closeEntry();
                        }
                    }
                }
                if(metadata==null)throw new Exception("ZIP sem database.json: não é backup do Minha Escala");
                JSONObject json=new JSONObject(new String(metadata,StandardCharsets.UTF_8));
                if(json.optInt("schema",0)!=2)throw new Exception("Versão de backup não reconhecida");
                ArrayList<LocalStore.Photo> photos=new ArrayList<>();
                ArrayList<LocalStore.Shift> shifts=new ArrayList<>();
                ArrayList<String> favs=new ArrayList<>();
                JSONArray p=json.optJSONArray("photos"),sh=json.optJSONArray("shifts"),f=json.optJSONArray("favorites");
                if(p!=null)for(int i=0;i<p.length();i++){
                    JSONObject obj=p.optJSONObject(i);if(obj==null)continue;
                    LocalStore.Photo item=LocalStore.Photo.fromJson(obj);
                    if(!item.file.matches("escala_[0-9_]+\\.(jpg|jpeg|png|webp|heic|bmp)"))throw new Exception("Nome inválido de foto no backup");
                    if(!new File(temp,item.file).isFile())throw new Exception("Foto ausente no ZIP: "+item.title);
                    photos.add(item);
                }
                if(sh!=null)for(int i=0;i<sh.length();i++){
                    JSONObject obj=sh.optJSONObject(i);if(obj!=null)shifts.add(LocalStore.Shift.fromJson(obj));
                }
                if(f!=null)for(int i=0;i<f.length();i++){
                    String favorite=f.optString(i,"");if(!favorite.trim().isEmpty())favs.add(favorite);
                }
                // Arquivos potencialmente grandes: copiar fora da interface Android.
                ArrayList<File> created=new ArrayList<>();
                try{
                    for(int i=0;i<photos.size();i++){
                        LocalStore.Photo photo=photos.get(i);
                        String original=photo.file;
                        String ext=original.substring(original.lastIndexOf('.')+1);
                        File dest=new File(getFilesDir(),"escala_"+System.nanoTime()+"_"+i+"."+ext);
                        created.add(dest);
                        try(FileInputStream src=new FileInputStream(new File(temp,original));
                            FileOutputStream dst=new FileOutputStream(dest)){
                            zipCopy(src,dst,35_000_000);
                        }
                        photo.file=dest.getName();
                    }
                }catch(Exception copyError){
                    for(File file:created)file.delete();deleteTree(temp);
                    throw copyError;
                }
                runOnUiThread(()->{
                    ArrayList<LocalStore.Photo> beforePhotos=new ArrayList<>(store.photos);
                    ArrayList<LocalStore.Shift> beforeShifts=new ArrayList<>(store.shifts);
                    ArrayList<String> beforeFavs=new ArrayList<>(store.favorites);
                    try{
                        store.photos.clear();store.photos.addAll(photos);
                        store.shifts.clear();store.shifts.addAll(shifts);
                        store.favorites.clear();store.favorites.addAll(favs);
                        if(!store.save())throw new Exception("Falha ao salvar banco restaurado");
                        searchVersion++;hasSearch=false;renderAll();
                        status("Backup restaurado: "+photos.size()+" foto(s), "+shifts.size()+" ficha(s).");
                        executor.execute(()->{
                            for(LocalStore.Photo old:beforePhotos)new File(getFilesDir(),old.file).delete();
                            deleteTree(temp);
                        });
                    }catch(Exception ex){
                        store.photos.clear();store.photos.addAll(beforePhotos);
                        store.shifts.clear();store.shifts.addAll(beforeShifts);
                        store.favorites.clear();store.favorites.addAll(beforeFavs);
                        store.save();
                        executor.execute(()->{for(File file:created)file.delete();deleteTree(temp);});
                        toast("Não foi possível restaurar: "+ex.getMessage());
                    }
                });
            }catch(Exception ex){runOnUiThread(()->{deleteTree(temp);
                status("Falha ao ler backup.");toast(ex.getMessage());});}
        });
    }
    private static void deleteTree(File file){
        if(file.isDirectory()){
            File[] files=file.listFiles();if(files!=null)for(File f:files)deleteTree(f);
        }
        file.delete();
    }
    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}return "foto.jpg";
    }
    private void importNext(){
        Uri uri=importQueue.poll();if(uri==null){processing=false;status("Fotos processadas. Toque em 🔍 Pesquisar para atualizar o resultado.");renderAll();return;}
        processing=true;int idx=++importIndex;
        status("Importando foto "+idx+" ("+importQueue.size()+" restantes)...");
        String title=displayName(uri);
        executor.execute(()->{
            File saved=null;
            try{
                String ext="jpg";int dot=title.lastIndexOf('.');if(dot>=0){
                    String e=title.substring(dot+1).toLowerCase(Locale.ROOT);
                    if(e.matches("jpeg|jpg|png|webp|heic|bmp"))ext=e;
                }
                String filename="escala_"+System.nanoTime()+"."+ext;
                saved=new File(getFilesDir(),filename);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                long total=0;try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(saved)){
                    if(in==null)throw new Exception("Foto não disponível");
                    byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){
                        total+=n;if(total>30_000_000)throw new Exception("Foto acima de 30 MB");
                        digest.update(b,0,n);out.write(b,0,n);
                    }
                }
                final File file=saved;LocalStore.Photo p=new LocalStore.Photo();
                p.id="p"+System.nanoTime();p.file=file.getName();p.title=title;
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&0xff));
                p.sha256=hash.toString();
                runOnUiThread(()->{
                    for(LocalStore.Photo old:store.photos)if(!old.sha256.isEmpty()&&old.sha256.equals(p.sha256)){
                        file.delete();status("Foto repetida ignorada: "+title);importNext();return;
                    }
                    recognize(p,()->importNext());
                });
            }catch(Exception ex){if(saved!=null)saved.delete();
                runOnUiThread(()->{toast("Erro em "+title+": "+ex.getMessage());importNext();});}
        });
    }
    private void recognize(LocalStore.Photo p,Runnable done){
        try {
            File file=new File(getFilesDir(),p.file);
            BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),size);p.width=size.outWidth;p.height=size.outHeight;
            InputImage image=InputImage.fromFilePath(this,Uri.fromFile(file));
            status("Lendo texto de "+p.title+" (offline)...");
            recognizer.process(image)
                .addOnSuccessListener(result->{
                    p.raw=result.getText();p.lines.clear();
                    for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                        LocalStore.Line l=new LocalStore.Line();l.text=line.getText();
                        Rect r=line.getBoundingBox();if(r!=null){l.left=r.left;l.top=r.top;l.right=r.right;l.bottom=r.bottom;}
                        p.lines.add(l);
                    }
                    if(!p.kindManual)p.kind=ScheduleParser.kind(p.raw);
                    if(p.date.isEmpty())p.date=ScheduleParser.getDate(p);
                    if(!store.photos.contains(p))store.photos.add(p);
                    if(!store.save())toast("Não foi possível gravar o índice local.");
                    renderAll();status("OCR concluído: "+p.title+" • "+p.lines.size()+" linhas.");
                    if(done!=null)done.run();
                })
                .addOnFailureListener(ex->{
                    if(!store.photos.contains(p))store.photos.add(p);
                    store.save();renderAll();status("Não foi possível ler: "+p.title);
                    toast("Você pode importar outra foto com mais nitidez.");if(done!=null)done.run();
                });
        }catch(Exception ex){status("Erro OCR: "+ex.getMessage());if(done!=null)done.run();}
    }
    private void exportResults(){
        String query=searchInput.getText().toString().trim();
        if(query.isEmpty()&&!onlyFavorites){toast("Pesquise um nome ou ative Favoritos primeiro.");return;}
        StringBuilder sb=new StringBuilder("\uFEFFTipo;Data;Nome/Linha;Horário sugerido;Setor;Fonte;Observação\r\n");
        for(LocalStore.Shift s:store.shifts)if((query.isEmpty()||ScheduleParser.matches(s.person,query))&&isVisibleDate(s.date)&&queryMatchesFavorite(s.person)){
            csvLine(sb,"CONFIRMADO",s.date,s.person,"Manha "+s.start+"-"+s.end+" / Tarde "+s.start2+"-"+s.end2,
                s.section,s.source,"MSC ID: "+s.mscId+" / Pausas: "+s.break1+"; "+s.break2+
                " / Treinamento: "+s.training+" / Notas: "+s.notes);
        }
        for(LocalStore.Photo p:store.photos)if(isVisibleDate(p.date)){
            ArrayList<ScheduleParser.Hit> list=new ArrayList<>();
            if(!query.isEmpty())list.addAll(ScheduleParser.search(p,query));
            else for(String f:store.favorites)list.addAll(ScheduleParser.search(p,f));
            for(ScheduleParser.Hit h:list)if(queryMatchesFavorite(h.matched)){
                csvLine(sb,"OCR - "+p.kind+" - CONFERIR",p.date,h.person,h.times,h.section,p.title,
                    "ID "+h.mscId+" | "+h.context);
            }
        }
        exportContent=sb.toString();
        new AlertDialog.Builder(this).setTitle("Exportar nomes e horários")
            .setMessage("O CSV pode conter dados pessoais de colegas. Guarde e compartilhe somente com autorização.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Exportar",(di,w)->{
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");intent.putExtra(Intent.EXTRA_TITLE,"minha_escala_pesquisa.csv");
                startActivityForResult(intent,REQ_EXPORT);
            }).show();
    }
    private static void csvLine(StringBuilder s,String... columns){
        for(int i=0;i<columns.length;i++){if(i>0)s.append(';');
            String v=columns[i]==null?"":columns[i].replace("\"","\"\"");
            s.append('"').append(v).append('"');
        }s.append("\r\n");
    }
    private void clearEverything(){
        new AlertDialog.Builder(this).setTitle("Apagar dados locais?")
            .setMessage("Esta ação apagará cópias das fotos, OCR, favoritos e horários confirmados DENTRO do aplicativo. A galeria original fica intacta.")
            .setNegativeButton("Cancelar",null).setPositiveButton("APAGAR",(d,w)->{
                for(LocalStore.Photo p:store.photos)new File(getFilesDir(),p.file).delete();
                searchVersion++;hasSearch=false;
                store.photos.clear();store.shifts.clear();store.favorites.clear();store.save();renderAll();
            }).show();
    }
    private void showInfo(){
        showLongText("Como funciona e privacidade",
            "1. Toque em Importar fotos e selecione várias imagens da escala.\n\n"+
            "2. O aplicativo usa OCR do ML Kit com modelo embutido. Não requer internet depois de instalado.\n\n"+
            "3. Digite um nome/MSC ID e toque em Pesquisar (ou Buscar no teclado). A busca não é executada durante a digitação.\n\n"+
            "4. Confira a foto original antes de confirmar horários, principalmente fotos inclinadas, borradas e tabelas com colunas apertadas.\n\n"+
            "5. Para salvar um turno verificado, use Corrigir/confirmar. O registro manual aparece como HORÁRIO CONFIRMADO.\n\n"+
            "6. Treinamentos, escalas, avisos, cadastros e trocas de cabine são categorias diferentes. Horas OCR são sugestões; confirme as COLUNAS na foto.\n\n"+
            "7. Se a data não foi identificada, abra Fotos importadas > Definir data.\n\n"+
            "8. Favoritos salvam nomes buscados e iniciam a busca ao tocar. Fotos exatamente repetidas são ignoradas na importação.\n\n"+
            "9. Salve o backup ZIP (inclui fotos + fichas), antes de trocar de celular ou reinstalar o aplicativo. Proteja esse arquivo, pois contém dados pessoais.\n\n"+
            "PRIVACIDADE: nomes, IDs e escalas são dados pessoais. Não publique fotos em repositórios GitHub públicos. O projeto de código NÃO contém as fotos fornecidas no chat.\n\n"+
            "LIMITES: o app não reconstrói perfeitamente todas as células; reconhecimentos OCR, horários e datas devem ser conferidos. Não gera alertas automáticos, não integra com sistemas do empregador e não fornece a versão oficial da escala.");
    }
    private void status(String message){if(statusText!=null)statusText.setText(message);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){
        super.onDestroy();if(recognizer!=null)recognizer.close();executor.shutdown();searchExecutor.shutdownNow();
    }
}
