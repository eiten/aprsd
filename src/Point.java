/* 
 * Copyright (C) 2010 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd;
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  

/**
 * Geographic position.
 * Every point has a location
 */
 
public abstract class Point implements Serializable
{             
    protected Reference   _position;
      
    public Point (Reference p)
       { _position = p; }
          
    
    /**
     * Test if position is inside of the rectangular area defined by uleft (upper left corner)
     * and lright (lower right corner). Assume that uleft and lright are within the same
     * UTM zone. 
     */          
    public boolean isInside(UTMRef uleft, UTMRef lright, double xext, double yext)
    {
        double xoff  = xext * (lright.getEasting()  - uleft.getEasting());
        double yoff = yext * (lright.getNorthing() - uleft.getNorthing());
    
         /* FIXME: Add lat zone as well */
        if (_position == null)
           return false;
        try {
           UTMRef ref = _position.toLatLng().toUTMRef(uleft.getLngZone());
           return ( ref.getEasting() >= uleft.getEasting()-xoff && ref.getNorthing() >= uleft.getNorthing()-yoff &&
                    ref.getEasting() <= lright.getEasting()+xoff && ref.getNorthing() <= lright.getNorthing()+yoff );
        }
        catch (Exception e) { return false; }
    }
    
    
    public boolean isInside(UTMRef uleft, UTMRef lright)
       { return isInside(uleft, lright, 0, 0); }
       
    public Reference getPosition ()   
       { return _position; } 

}