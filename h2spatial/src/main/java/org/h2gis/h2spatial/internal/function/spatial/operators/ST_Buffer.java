/**
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2007-2014 IRSTV (FR CNRS 2488)
 *
 * h2patial is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2spatial is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2spatial. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */

package org.h2gis.h2spatial.internal.function.spatial.operators;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.buffer.BufferOp;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import java.sql.SQLException;
import org.h2.value.Value;
import org.h2.value.ValueInt;
import org.h2.value.ValueString;

import org.h2gis.h2spatialapi.DeterministicScalarFunction;

/**
 * ST_Buffer computes a buffer around a Geometry.  Circular arcs are
 * approximated using 8 segments per quadrant. In particular, circles contain
 * 32 line segments.
 *
 * @author Nicolas Fortin, Erwan Bocher
 */
public class ST_Buffer extends DeterministicScalarFunction {


    /**
     * Default constructor
     */
    public ST_Buffer() {
        addProperty(PROP_REMARKS, "Compute a buffer around a Geometry.\n"
                + "The optional third parameter can either specify number of segments used\n"
                + " to approximate a quarter circle (integer case, defaults to 8)\n"
                + " or a list of blank-separated key=value pairs (string case) to manage buffer style parameters :\n"
                + "'quad_segs=8' endcap=round|flat|square' 'join=round|mitre|bevel' 'mitre_limit=5'");
    }

    @Override
    public String getJavaStaticMethod() {
        return "buffer";
    }

    /**
     * @param a Geometry instance.
     * @param distance Buffer width in projection unit
     * @return a buffer around a geometry.
     */
    public static Geometry buffer(Geometry a,Double distance) {
        if(a==null || distance==null) {
            return null;
        }
        return a.buffer(distance);
    }
    
    /**
     * @param a Geometry instance.
     * @param distance Buffer width in projection unit
     * @param bufferParameters
     * @return a buffer around a geometry.
     */
    public static Geometry buffer(Geometry geom,Double distance, Value value) throws SQLException {        
        if(value instanceof ValueString){
            String[] buffParemeters = value.getString().split("\\s+");  
            BufferParameters bufferParameters = new BufferParameters();
            for (String params : buffParemeters) {
                String[] keyValue = params.split("=");
                if(keyValue[0].equalsIgnoreCase("endcap")){
                    String param = keyValue[1];
                    if(param.equalsIgnoreCase("round")){
                        bufferParameters.setEndCapStyle(BufferParameters.CAP_ROUND);
                    }
                    else if(param.equalsIgnoreCase("flat") || param.equalsIgnoreCase("butt")){
                        bufferParameters.setEndCapStyle(BufferParameters.CAP_FLAT);
                    }
                    else if(param.equalsIgnoreCase("square")){
                        bufferParameters.setEndCapStyle(BufferParameters.CAP_SQUARE);
                    }
                    else{
                        throw new SQLException("Supported join values are round, flat, butt or square.");
                    }                    
                }
                else if(keyValue[0].equalsIgnoreCase("join")){
                    String param = keyValue[1];
                    if(param.equalsIgnoreCase("bevel")){
                        bufferParameters.setJoinStyle(BufferParameters.JOIN_BEVEL);
                    }
                    else if(param.equalsIgnoreCase("mitre")||param.equalsIgnoreCase("miter")){
                        bufferParameters.setJoinStyle(BufferParameters.JOIN_MITRE);
                    }
                    else if(param.equalsIgnoreCase("round")){
                        bufferParameters.setJoinStyle(BufferParameters.JOIN_ROUND);
                    }
                    else{
                        throw new SQLException("Supported join values are bevel, mitre, miter or round.");
                    }             
                }
                else if(keyValue[0].equalsIgnoreCase("mitre_limit")||keyValue[0].equalsIgnoreCase("miter_limit")){
                    bufferParameters.setMitreLimit(Double.valueOf(keyValue[1]));
                }
                else if(keyValue[0].equalsIgnoreCase("quad_segs")){
                    bufferParameters.setQuadrantSegments(Integer.valueOf(keyValue[1]));
                }
                else{
                    throw new SQLException("Unknown parameters please read the documentation.");
                }
            }            
            BufferOp bufOp  = new BufferOp(geom, bufferParameters);            
            return bufOp.getResultGeometry(distance);
        }
        else if (value instanceof ValueInt){
            BufferOp bufOp  = new BufferOp(geom, new BufferParameters(value.getInt()));
            return bufOp.getResultGeometry(distance);
        }
        else {
            throw new SQLException("The third argument must be an int or a varchar.");
        }
    }
}