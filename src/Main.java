
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;


public class Main
{
   public static String version = "1.0.1";

   public static InetChannel ch1 = null;
   public static TncChannel  ch2 = null;
   public static Igate igate  = null;
   public static OwnObjects ownobjects; 
   public static RemoteCtl  rctl;
   public static SarMode    sarmode = null;
   protected static StationDB db = null;
 
   public static void main( String[] args )
   {
        /* Get properties from configfile */
        if (args.length < 1)
           System.out.println("Usage: Daemon <config-file>");
           
        Properties config = new Properties();
          
        Runtime.getRuntime().addShutdownHook( new Thread() 
             {
                public void run() 
                   { 
                     System.out.println("*** Polaric APRSD shutdown"); 
                     if (db  != null) db.save(); 
                     if (ch1 != null) ch1.close();
                     if (ch2 != null) ch2.close();
                   } 
             });
             
        try {
           FileInputStream fin = new FileInputStream(args[0]);
           config.load(fin);
           System.out.println( "*** Polaric APRSD startup" );
           
           
           /* Database of stations/objects */
           db  = new StationDBImp(config); 
           AprsPoint.setDB(db);

           /* Start parser and connect it to channel(s) if any */
           AprsParser p = new AprsParser(db, db.getMsgProcessor());
           if (config.getProperty("igate.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate IGATE");
               igate = new Igate(config);
           }
           if (config.getProperty("inetchannel.on", "false").trim().matches("true|yes"))  {
               System.out.println("*** Activate Internet Channel");
               ch1 = new InetChannel(config);
               ch1.setReceivers((Channel.Receiver) p, igate); 
               Thread t = new Thread(ch1, "InetChannel");
               t.start(); 
           }
           if (config.getProperty("tncchannel.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate TNC Channel");
               ch2 = new TncChannel(config);
               ch2.setReceivers((Channel.Receiver) p, igate);
               Thread t = new Thread(ch2, "TncChannel");
               t.start(); 
           } 
           if (config.getProperty("remotectl.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate Remote Control");
               rctl = new RemoteCtl(config, db.getMsgProcessor(), db);
           }
 
           
           /* Message processing */
           db.getMsgProcessor().setChannels(ch2, ch1);  

           /* Igate */  
           if (igate != null)
               igate.setChannels(ch2, ch1);

           /* APRS objects */
           ownobjects = db.getOwnObjects(); 
           ownobjects.setChannels(ch2, ch1); 
           
           /* Start HTTP server */
           int http_port = Integer.parseInt(config.getProperty("httpserver.port", "8081"));

           Class cls = Class.forName("no.polaric.aprsd.http.Webserver");
           Constructor con = cls.getConstructors()[0];
           Object ws = con.newInstance(db, http_port, config);
           
           System.out.println( "*** HTTP server ready on port " + http_port);
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't start HTTP server:\n");
             ioe.printStackTrace(System.err);
             System.exit( -1 );
        }
        
   }
}
