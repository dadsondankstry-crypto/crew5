package com.exemplo.minhaescala;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser CONSERVADOR do OCR. Nao transforma horarios encontrados em colunas
 * desconhecidas em horarios de trabalho confirmados. A foto e a fonte primaria.
 */
public final class ScheduleParser {
    private ScheduleParser() {}
    private static final Pattern DATE = Pattern.compile("(?<!\\d)([0-3]?\\d)[/\\-.]([01]?\\d)[/\\-.]((?:20)?\\d{2})(?!\\d)");
    private static final Pattern DATE_EN = Pattern.compile("(?i)(?<!\\d)([0-3]?\\d)[. /-]+(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[. /-]+((?:20)?\\d{2})(?!\\d)");
    private static final Pattern TIME = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])[:h.]([0-5]\\d)(?!\\d)");
    private static final Pattern MSC = Pattern.compile("(?i)(?<![A-Za-z])msc\\s*[:#-]?\\s*(\\d{3,8})\\b");
    private static final Pattern TRAINING_TIME = Pattern.compile("(?i)\\bTIME\\s*[:=-]?\\s*([01]?\\d|2[0-3])\\s*[:.]\\s*([0-5]\\d)");
    private static final Pattern DECK = Pattern.compile("(?i)\\bDECK\\s*[0-9]+\\s*[-–—:]\\s*[^\\r\\n]{2,70}");

    public static String normalize(String s) {
        if(s==null)return "";
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+"," ")
                .trim().replaceAll("\\s+"," ");
    }
    public static boolean matches(String text,String query) {
        String s=normalize(text), q=normalize(query);
        if(q.isEmpty())return true;
        if(s.contains(q))return true;
        for(String part:q.split(" "))if(!s.contains(part))return false;
        return true;
    }
    private static String formatDate(int day,int month,int year) {
        try {
            if(year<100)year+=2000;
            SimpleDateFormat format=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);
            format.setLenient(false);
            Date date=format.parse(String.format(Locale.ROOT,"%02d/%02d/%04d",day,month,year));
            return format.format(date);
        }catch(Exception ignored){return "";}
    }
    public static String normalizeDate(String s) {
        if(s==null)return "";
        Matcher m=DATE.matcher(s);
        if(m.find())return formatDate(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
        Matcher en=DATE_EN.matcher(s);
        if(en.find()) {
            String mon=en.group(2).toLowerCase(Locale.ROOT).substring(0,3);
            String[] months={"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};
            for(int i=0;i<months.length;i++)if(months[i].equals(mon))
                return formatDate(Integer.parseInt(en.group(1)),i+1,Integer.parseInt(en.group(3)));
        }
        return "";
    }
    public static String kind(String raw) {
        String s=normalize(raw);
        // Classifica pelo CABECALHO do documento, nao apenas pela palavra TRAINING,
        // comum na coluna de observacoes de uma escala de trabalho.
        if(s.contains("crew cabin change"))return "CABINES";
        if(s.contains("learning and development training") ||
            (s.contains("attendance is mandatory") && (s.contains("deck")||s.contains("ot alio"))))return "TREINAMENTO";
        if(s.contains("breakfast duty")||s.contains("lunch duty")||s.contains("dinner duty")||
            s.contains("start time")||s.contains("end time")||s.contains("cleaning team") ||
            s.contains("breakfast")&&s.contains("lunch"))return "ESCALA";
        if(s.contains("date embark")&&s.contains("position"))return "CADASTRO";
        if(s.contains("announcement")||s.contains("important reminder")||s.contains("dear all"))return "AVISO";
        return "ESCALA";
    }
    public static String getDate(LocalStore.Photo p) {
        if("CADASTRO".equals(p.kind)||"CABINES".equals(p.kind))return "";
        ArrayList<LocalStore.Line> sorted=new ArrayList<>(p.lines);
        Collections.sort(sorted,Comparator.comparingInt(a->a.top));
        for(LocalStore.Line l:sorted) {
            String lower=normalize(l.text);
            if(lower.contains("date embark")||lower.contains("embark"))continue;
            // No cartaz de treinamento, a data pode ficar na tabela de presencas,
            // mas somente aceitamos data com mes escrito (05.Nov.2026).
            if("TREINAMENTO".equals(p.kind)) {
                if(DATE_EN.matcher(l.text).find())return normalizeDate(l.text);
                continue;
            }
            if(p.height>0 && l.top>p.height*.38)break;
            String date=normalizeDate(l.text);if(!date.isEmpty())return date;
        }
        return "";
    }
    public static ArrayList<String> times(String text) {
        ArrayList<String> out=new ArrayList<>();Matcher m=TIME.matcher(text==null?"":text);
        while(m.find() && out.size()<16){
            String tm=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(m.group(1)),m.group(2));
            if(!out.contains(tm))out.add(tm);
        }
        return out;
    }
    public static String mscId(String text) {
        Matcher m=MSC.matcher(text==null?"":text);return m.find()?"MSC"+m.group(1):"";
    }
    public static String cleanPerson(String text) {
        if(text==null)return "";
        String result=text.replaceAll("(?i)^\\s*\\d{0,2}\\s*msc\\s*[:#-]?\\s*\\d{3,8}","");
        result=result.replaceAll("^[\\s|;:,\\d]+","").replaceAll("[|•]+", " ").trim();
        return result.isEmpty()?text.trim():result;
    }
    private static String cleanRow(String s) {
        return s==null?"":s.replaceAll("\\s+"," ").trim();
    }
    private static String firstTrainingTime(LocalStore.Photo p) {
        Matcher m=TRAINING_TIME.matcher(p.raw==null?"":p.raw);
        if(m.find())return String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(m.group(1)),m.group(2));
        return "";
    }
    private static String trainingVenue(LocalStore.Photo p) {
        Matcher m=DECK.matcher(p.raw==null?"":p.raw);
        if(m.find())return cleanRow(m.group());
        return "Local a conferir na foto";
    }
    private static String sectionAbove(ArrayList<LocalStore.Line> sorted,int y) {
        String result="";int deltaBest=Integer.MAX_VALUE;
        for(LocalStore.Line l:sorted){
            int delta=y-l.cy();if(delta<=0||delta>360||delta>=deltaBest)continue;
            String s=normalize(l.text);
            if(s.contains("restaurant")||s.contains("buffet")||s.contains("wine cellar")||
               s.contains("poppa crystal")||s.contains("room service")||s.contains("linen team")||
               s.contains("oleandro")||s.contains("team waiter")||s.contains("specialty support")||
               s.contains("inventory team")||s.contains("mdh office")||s.contains("captain s waiter")||
               s.contains("breakfast")||s.contains("lunch")||s.contains("dinner")||s.contains("dk5")||s.contains("dk6")||s.contains("dk7")||s.contains("dk13")) {
                result=l.text;deltaBest=delta;
            }
        }
        return result;
    }
    public static final class Hit {
        public LocalStore.Photo photo;
        public String matched="",person="",mscId="",context="",section="",times="",description="";
        public int y;
        public boolean correction;
    }
    public static ArrayList<Hit> search(LocalStore.Photo p,String query) {
        ArrayList<Hit> out=new ArrayList<>();if(normalize(query).isEmpty())return out;
        // Avisos e troca de cabines nao devem aparecer como escala/treinamento.
        if("AVISO".equals(p.kind)||"CABINES".equals(p.kind))return out;
        ArrayList<LocalStore.Line> lines=new ArrayList<>(p.lines);
        Collections.sort(lines,Comparator.comparingInt((LocalStore.Line l)->l.top).thenComparingInt(l->l.left));
        HashSet<Integer> seen=new HashSet<>();
        for(LocalStore.Line line:lines){
            if(!matches(line.text,query))continue;
            int y=line.cy(),tol=Math.max(13,Math.min(25,line.bottom-line.top+8));
            boolean repeated=false;
            for(Integer old:seen)if(Math.abs(old-y)<=tol){repeated=true;break;}
            if(repeated)continue;
            seen.add(y);
            ArrayList<LocalStore.Line> row=new ArrayList<>();
            for(LocalStore.Line other:lines)if(Math.abs(other.cy()-y)<=tol)row.add(other);
            Collections.sort(row,Comparator.comparingInt(a->a.left));
            StringBuilder sb=new StringBuilder();for(LocalStore.Line r:row){if(sb.length()>0)sb.append(" | ");sb.append(r.text);}
            String context=sb.toString();
            Hit h=new Hit();h.photo=p;h.matched=line.text;h.context=context;h.y=y;
            h.mscId=mscId(context);h.person=cleanPerson(line.text);
            // Quando o OCR isolou o ID, tenta usar a proxima celula na mesma linha.
            if(h.person.length()<3||normalize(h.person).matches("msc[0-9]+")){
                for(LocalStore.Line r:row)if(r!=line && r.left>=line.left && r.text.matches(".*[A-Za-zÀ-ÿ]{3}.*")){
                    h.person=cleanPerson(r.text);break;
                }
            }
            if("TREINAMENTO".equals(p.kind)){
                h.section=trainingVenue(p);
                String t=firstTrainingTime(p);
                h.times=t.isEmpty()?"Horário do treinamento: confirmar":("Treinamento anunciado: "+t);
                h.description="Documento de treinamento, NAO e turno de trabalho.";
            }else if("CADASTRO".equals(p.kind)){
                h.times="Sem horario de turno nesta lista";
                h.section="Cadastro de tripulantes";
                h.description="Data de embarque NAO e data da escala.";
            }else{
                h.section=sectionAbove(lines,y);
                ArrayList<String> all=times(context), filtered=new ArrayList<>();
                for(String t:all)if(!"00:00".equals(t) && filtered.size()<7)filtered.add(t);
                StringBuilder tm=new StringBuilder();for(String part:filtered){if(tm.length()>0)tm.append(" · ");tm.append(part);}
                h.times=filtered.isEmpty()?"Horarios: conferir na foto":("Horarios lidos (sem associar colunas): "+tm);
                h.description="Confira na imagem quais horarios sao de trabalho, pausa e treinamento.";
            }
            out.add(h);if(out.size()>=30)break;
        }
        if(!p.corrected.trim().isEmpty()){
            for(String s:p.corrected.split("\\r?\\n"))if(matches(s,query)){
                Hit h=new Hit();h.photo=p;h.matched=s;h.person=cleanPerson(s);
                h.context="Texto corrigido manualmente";h.description="Linha corrigida; confira na foto";
                h.times="Conferir horario na imagem";h.correction=true;out.add(h);
            }
        }
        return out;
    }
    public static String today(){return new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(new Date());}
}
