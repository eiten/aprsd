

package no.polaric.aprsd.http;
 
public interface ItemInfo {

    public static class Alias {
        public String alias; 
        public String icon; 
        
        public Alias() 
          {}
        public Alias(String a, String ic)
          {alias=a; icon=ic; }
    }
}
