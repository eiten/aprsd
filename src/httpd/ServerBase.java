/* 
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import no.polaric.aprsd.filter.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.util.function.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 
import org.xnap.commons.i18n.*;
import spark.*;
import com.fasterxml.jackson.databind.*;


// FIXME: Move some of this to a ServerConfig object. Move out the XML stuff (remove it later). 

public abstract class ServerBase 
{
   protected  ServerAPI  _api;
   private    String     _timezone;
   protected  String     _wfiledir;
   private    String     _adminuser, _updateusers;
   protected  String     _icon;    
   
   public static final String _encoding = "UTF-8";

   static DateFormat df = new SimpleDateFormat("dd MMM. HH:mm",
            new DateFormatSymbols(new Locale("no")));
   static DateFormat tf = new SimpleDateFormat("HH:mm",
            new DateFormatSymbols(new Locale("no")));
   static DateFormat xf = new SimpleDateFormat("yyyyMMddHHmmss",
            new DateFormatSymbols(new Locale("no")));       
   static DateFormat isodf = 
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            
   public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   public static Calendar localTime = Calendar.getInstance();
   
   
      /* Jackson JSON mapper */ 
   protected final static ObjectMapper mapper = new ObjectMapper();
   
   
   public static String toJson(Object obj) 
       { return serializeJson(obj); }
 
 
    public static Object fromJson(String text, Class cls) 
       { return deserializeJson(text, cls); }
 
 
   public static String serializeJson(Object obj) {
       try {
            mapper.setDateFormat(isodf);
            return mapper.writeValueAsString(obj);
       }
       catch (Exception e) {
            return null;
       }
   }
   
   
   public static Object deserializeJson(String text, Class cls) {
     try {
        return mapper.readValue(text, cls);
     }
     catch (Exception e) {
        return null;
     }
   }
   
   
   public void systemNotification(String user, String txt, int ttl) {
        _api.getWebserver().notifyUser(user, 
            new ServerAPI.Notification("system", "system", txt, new Date(), ttl) );  
   }
   
   
   
   
   public ServerBase(ServerAPI api) 
   {
      _api=api; 
      _wfiledir    = api.getProperty("map.web.dir", "aprsd");
      _icon        = api.getProperty("map.icon.default", "sym.gif");
      _timezone    = api.getProperty("timezone", "");
      _adminuser   = api.getProperty("user.admin", "admin");
      _updateusers = api.getProperty("user.update", "");
                
      TimeZone.setDefault(null);
      if (_timezone.length() > 1) {
          TimeZone z = TimeZone.getTimeZone(_timezone);
          localTime.setTimeZone(z);
          df.setTimeZone(z);
          tf.setTimeZone(z);
      }
   }
   
   
   private static final String i18n_base = "no.polaric.aprsd.http";
   
      
   public I18n getI18n(Request req)
       { return _getI18n(req, i18n_base); }
       
   public static I18n _getI18n(Request req)
       { return _getI18n(req, i18n_base); }
       
   public I18n getI18n(Request req, String base)
       { return _getI18n(req, base); }
         
   public static I18n _getI18n(Request req, String base) 
   {
        if (base==null)
           base = i18n_base; 
        String lang = req.queryParams("lang");         
        Locale loc = ((lang != null && lang.length() > 0) ? new Locale(lang) : new Locale("en"));
        return I18nFactory.getI18n(base + ".XX", "i18n.Messages", 
                       ServerBase.class.getClassLoader(), loc, I18nFactory.FALLBACK);
   }
   
   
   protected ServerAPI getApi() 
       { return _api; }
   
   
   protected static double roundDeg(double x)
       { return ((double) Math.round(x*100000)) / 100000; 
       }
  


   /**
    * Convert reference to UTM projection with our chosen zone.
    * Return null if it cannot be converted to our zone (too far away).
    */
   protected UTMRef toUTM(Reference ref, int utmz)
   {
        try { return ref.toLatLng().toUTMRef(utmz); }
        catch (Exception e)
           { _api.log().warn("ServerBase", "Cannot convert to UTM"+ utmz+ " : "+ref);
             return null; }
   }
   
   
       
    /*
     * Log, notify admin and other server about change of alias 
     */
    protected void notifyAlias(String ident, String alias, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ALIAS "+ident+" "+alias, null);
        _api.log().info("SystemApi", 
            "ALIAS: '"+alias+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Alias: '"+alias+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    

    /*
     * Log, notify admin and other server about change of icon 
     */
    protected void notifyIcon(String ident, String icon, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ICON "+ident+" "+icon, null);
        _api.log().info("SystemApi", 
            "ICON: '"+icon+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Icon: '"+icon+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    } 
   
   
   
   /**
    * Get username of the authenticated user. 
    * @return username, null if not authenticated. 
    */
   protected final AuthInfo getAuthInfo(Request req)
      { return WebServer.getAuthInfo(req); }

   

   /**
    * Sanitize text input that can be used in HTML output. 
    */
   protected String fixText(String t)
   {  
        if (t==null)
           return t; 
        StringBuilder sb = new StringBuilder(t);
        int length = t.length();
        for (int i=0; i<length; i++) {
           switch (sb.charAt(i)) {
              case '&': 
                   if (sb.substring(i+1).matches("(amp|lt|gt|quot);.*")) 
                      i = sb.indexOf(";", i)+1;
                   else {
                      sb.insert(i+1,"amp;");
                      i+= 4;
                      length += 4;
                   }
                   break;
                
              case '<': 
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "lt;");
                   length += 3;
                   break;
              case '>': 
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "gt;");
                   length += 3;
                   break;
              case '"':
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "quot;");
                   length += 5;
                   break;
           }        
           if (sb.charAt(i)<' ')
              sb.setCharAt(i, '?');
        }
        return sb.toString(); 
   }
   
  

   protected String showDMstring(double ll)
   {
       int deg = (int) Math.floor(ll);
       double minx = ll - deg;
       if (ll < 0 && minx != 0.0) 
          minx = 1 - minx;
       
       double mins = ((double) Math.round( minx * 60 * 100)) / 100;
       return ""+deg+"\u00B0 "+mins+"'";
   }

   
   
   protected String ll2dmString(LatLng llref)
      { return showDMstring(llref.getLatitude())+"N, "+showDMstring(llref.getLongitude())+"E"; }
   
   

   
   
   
   protected String metaTag(String name, String val) 
      { return "<meta name=\""+name+"\" value=\""+val+"\"/>"; }
   

   
   /**
    * Display a message path between nodes. 
    */
   protected void printPathXml(PrintWriter out, Station s, Reference uleft, Reference lright)
   {
       LatLng ity = s.getPosition().toLatLng();
       Set<String> from = s.getTrafficTo();
       if (from == null || from.isEmpty()) 
           return;
       
       Iterator<String> it = from.iterator();    
       while (it.hasNext()) 
       {
            Station p = (Station)_api.getDB().getItem(it.next(), null);
            if (p==null || !p.isInside(uleft, lright) || p.expired())
                continue;
                
            LatLng itx = p.getPosition().toLatLng();
            RouteInfo.Edge e = _api.getDB().getRoutes().getEdge(s.getIdent(), p.getIdent());
            if (itx != null) { 
               out.print("<linestring stroke="+
                   (e.primary ? "\"2\"" : "\"1\"")  + " opacity=\"1.0\" color=\""  +
                   (e.primary ? "B00\">" : "00A\">"));
               out.print( roundDeg(itx.getLng()) + " " + roundDeg(itx.getLat()) );
               out.print(", ");
               out.print(roundDeg( ity.getLng()) + " " + roundDeg(ity.getLat()) );
               out.println("</linestring>");
            }
       }
   }
   
   
   
   
    protected void printTrailXml(PrintWriter out, String[] tcolor, 
          Reference firstpos, Seq<TPoint> h)  
    {
        String pre  = "   <linestring stroke=\"2\" opacity=\"1.0\" color=\""+ tcolor[0] +"\" color2=\""+ tcolor[1] +"\">";
        String post = "   </linestring>";     
        printPoints(out, pre, post, firstpos, h); 
    }
    
    
    
    protected void printPointCloud(PrintWriter out, String color, Seq<TPoint> h)  
    {
        String pre  = "   <pointcloud opacity=\"0.6\" color2=\""+color+"\">";
        String post = "   </pointcloud>";    
        printPoints(out, pre, post, null, h);
    }   
   
   
   
   
   /** 
    * Print a history trail of a moving station as a XML linestring object. 
    */
   protected void printPoints(PrintWriter out, String pre, String post, 
          Reference firstpos, Seq<TPoint> h)
   {
       TrailView tv = new TrailView(out, pre, post, firstpos);
       h.forEach( it -> tv.addPoint(it) );
       tv.close();
   }
 
 
 
  int _cnt = 0;
   /** 
    * Print XML overlay to the given output stream.
    */
   protected void printOverlay(String meta, PrintWriter out, int seq, String filt, 
                             long scale, LatLng uleft, LatLng lright, 
                             boolean loggedIn, boolean metaonly, boolean showSarInfo) 
               throws IOException
   {       
        RuleSet vfilt = ViewFilter.getFilter(filt, loggedIn);
        
        /* XML header with meta information */           
        out.println("<overlay seq=\""+seq+"\"" +
            (filt==null ? ""  : " view=\"" + filt + "\"") + ">");
            
        out.println(meta);
        
        /* Could we put metadata in a separate service? */
        if (metaonly) {
             out.println(metaTag("metaonly", "true")); 
             out.println("</overlay>");            
             return;
        }
        out.println(metaTag("metaonly", "false"));
        
        
         
        /* Output signs. A sign is not an APRS object
         * just a small icon and a title. It may be a better idea to do this
         * in map-layers instead?
         */
        for (Signs.Item s: Signs.search(scale, uleft, lright))
        {
            LatLng ref = s.getPosition().toLatLng(); 
            if (ref == null)
                continue;
            String href = s.getUrl() == null ? "" : "href=\"" + s.getUrl() + "\"";
            String title = s.getDescr() == null ? "" : "title=\"" + fixText(s.getDescr()) + "\"";
            String icon = _wfiledir + "/icons/"+ s.getIcon();    
           
            out.println("<point id=\""+ s.getIdent() + "\" x=\""
                         + roundDeg(ref.getLng()) + "\" y=\"" + roundDeg(ref.getLat()) + "\" " 
                         + href + " " + title+">");
            out.println(" <icon src=\""+icon+"\"  w=\"22\" h=\"22\" ></icon>");     
            out.println("</point>");    
        }              
                
        /* Output APRS objects */
        if (_api.getDB() == null)
           _api.log().error("ServerBase", "_api.getDB() returns null");
        else   
        for (TrackerPoint s: _api.getDB().search(uleft, lright)) 
        {
            Action action = vfilt.apply(s, scale); 
            // FIXME: Get CSS class from filter rules 
            
            if (s.getPosition() == null)
                continue; 
            if (s.getSource().isRestricted() && !action.isPublic() && !loggedIn)
                continue;
            if (action.hideAll())
                continue;
            
            LatLng ref = s.getPosition().toLatLng(); 
            if (ref == null) continue; 
            
            if (!s.visible() || (_api.getSar() != null && !loggedIn && _api.getSar().filter(s)))  
                   out.println("<delete id=\""+s.getIdent()+"\"/>");
            else {               
                  ref = s.getPosition().toLatLng(); 
                  if (ref == null) continue; 
                  
                  String title = s.getDescr() == null ? "" 
                             : " title=\"" + fixText(s.getDescr()) + "\""; 
                  String flags = " flags=\""+
                       (s.hasTag("APRS.telemetry") ? "t":"") +
                       (s instanceof AprsPoint ? "a":"") + 
                       (s instanceof AprsPoint && ((AprsPoint)s).isInfra() ? "i" : "") + "\"";
                  
                  String icon = action.getIcon(s.getIcon()); 
                  if (s.iconOverride() && showSarInfo) 
                     icon = s.getIcon(); 
                  icon = _wfiledir + "/icons/"+ (icon != null ? icon : _icon);    
                  
                  // FIXME: Sanitize ident input somewhere else
                  out.println("<point id=\""+s.getIdent()+"\" x=\""
                               + roundDeg(ref.getLng()) + "\" y=\"" + roundDeg(ref.getLat()) + "\"" 
                               + title + flags + (s.isChanging() ? " redraw=\"true\"" : "") +
                               ((s instanceof AprsObject) && _api.getDB().getOwnObjects().hasObject(s.getIdent().replaceFirst("@.*",""))  ? " own=\"true\"":"") +">");
                  out.println(" <icon src=\""+icon+"\"  w=\"22\" h=\"22\" ></icon>");     
                  
                  /* Show label */ 
                  if (!action.hideIdent() && !s.isLabelHidden() ) {
                     String style = (!(s.getTrail().isEmpty()) ? "lmoving" : "lstill");
                     if (s instanceof AprsObject)
                        style = "lobject"; 
                     style += " "+ action.getStyle();
                     
                     out.println(" <label style=\""+style+"\">");
                     out.println("   "+s.getDisplayId(showSarInfo));
                     out.println(" </label>"); 
                  }
                           
               /* Trail */
               Seq<TPoint> h = s.getTrail()
                  .subTrail(action.getTrailTime(), action.getTrailLen(), tp -> tp.isInside(uleft, lright, 0.7, 0.7) );     
               if (!action.hideTrail() && !h.isEmpty())
                  printTrailXml(out, s.getTrailColor(), s.getPosition(), h); 

               
               if (action.showPath() && s instanceof AprsPoint && ((AprsPoint)s).isInfra())
                  printPathXml(out, (Station) s, uleft, lright);              
               out.println("</point>");
            }
            
            /* Allow other threads to run */ 
            Thread.currentThread().yield ();
        }        
        out.println("</overlay>");
   }
 
 
}
