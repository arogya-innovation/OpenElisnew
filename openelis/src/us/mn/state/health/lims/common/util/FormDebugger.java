package us.mn.state.health.lims.common.util;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.struts.action.ActionForm;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyDescriptor;
import java.util.Enumeration;

/**
 * Debugging utility to help diagnose form and request data
 */
public class FormDebugger {
    
    /**
     * Print all properties available in the form
     */
    public static void dumpFormProperties(ActionForm form) {
        if (form == null) {
            System.out.println("Form is null");
            return;
        }
        
        System.out.println("========================================");
        System.out.println("FORM PROPERTIES DEBUG");
        System.out.println("Form class: " + form.getClass().getName());
        System.out.println("========================================");
        
        try {
            PropertyDescriptor[] properties = PropertyUtils.getPropertyDescriptors(form);
            
            for (PropertyDescriptor prop : properties) {
                if (prop.getReadMethod() != null) {
                    String propName = prop.getName();
                    try {
                        Object value = PropertyUtils.getProperty(form, propName);
                        String valueStr = (value == null) ? "null" : value.toString();
                        
                        // Truncate long values
                        if (valueStr.length() > 100) {
                            valueStr = valueStr.substring(0, 97) + "...";
                        }
                        
                        System.out.println("  " + propName + " = " + valueStr);
                    } catch (Exception e) {
                        System.out.println("  " + propName + " = <error reading: " + e.getMessage() + ">");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error dumping form properties: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("========================================");
    }
    
    /**
     * Print all request parameters
     */
    public static void dumpRequestParameters(HttpServletRequest request) {
        if (request == null) {
            System.out.println("Request is null");
            return;
        }
        
        System.out.println("========================================");
        System.out.println("REQUEST PARAMETERS DEBUG");
        System.out.println("========================================");
        
        Enumeration<String> paramNames = request.getParameterNames();
        
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            
            if (paramValues.length == 1) {
                System.out.println("  " + paramName + " = " + paramValues[0]);
            } else {
                System.out.print("  " + paramName + " = [");
                for (int i = 0; i < paramValues.length; i++) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(paramValues[i]);
                }
                System.out.println("]");
            }
        }
        
        System.out.println("========================================");
    }
    
    /**
     * Print all request attributes
     */
    public static void dumpRequestAttributes(HttpServletRequest request) {
        if (request == null) {
            System.out.println("Request is null");
            return;
        }
        
        System.out.println("========================================");
        System.out.println("REQUEST ATTRIBUTES DEBUG");
        System.out.println("========================================");
        
        Enumeration<String> attrNames = request.getAttributeNames();
        
        while (attrNames.hasMoreElements()) {
            String attrName = attrNames.nextElement();
            Object attrValue = request.getAttribute(attrName);
            String valueStr = (attrValue == null) ? "null" : attrValue.toString();
            
            // Truncate long values
            if (valueStr.length() > 100) {
                valueStr = valueStr.substring(0, 97) + "...";
            }
            
            System.out.println("  " + attrName + " = " + valueStr);
        }
        
        System.out.println("========================================");
    }
    
    /**
     * Print all session attributes
     */
    public static void dumpSessionAttributes(HttpServletRequest request) {
        if (request == null || request.getSession(false) == null) {
            System.out.println("Session is null");
            return;
        }
        
        System.out.println("========================================");
        System.out.println("SESSION ATTRIBUTES DEBUG");
        System.out.println("========================================");
        
        Enumeration<String> attrNames = request.getSession().getAttributeNames();
        
        while (attrNames.hasMoreElements()) {
            String attrName = attrNames.nextElement();
            Object attrValue = request.getSession().getAttribute(attrName);
            String valueStr = (attrValue == null) ? "null" : attrValue.toString();
            
            // Truncate long values
            if (valueStr.length() > 100) {
                valueStr = valueStr.substring(0, 97) + "...";
            }
            
            System.out.println("  " + attrName + " = " + valueStr);
        }
        
        System.out.println("========================================");
    }
    
    /**
     * Dump everything for comprehensive debugging
     */
    public static void dumpAll(HttpServletRequest request, ActionForm form) {
        dumpRequestParameters(request);
        dumpRequestAttributes(request);
        dumpSessionAttributes(request);
        dumpFormProperties(form);
    }
}