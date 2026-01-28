package us.mn.state.health.lims.patient.util;

import us.mn.state.health.lims.hibernate.HibernateUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Helper utility for patient UUID operations
 * Provides centralized methods to retrieve patient UUID from various sources
 */
public class PatientUuidHelper {
    
    /**
     * Get patient UUID by patient ID
     */
    public static String getPatientUuidById(String patientId) {
        if (patientId == null || patientId.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            String sql = "SELECT uuid FROM clinlims.patient WHERE id = ?";
            ps = conn.prepareStatement(sql);
            
            try {
                ps.setInt(1, Integer.parseInt(patientId));
            } catch (NumberFormatException e) {
                System.err.println("Invalid patient ID format: " + patientId);
                return null;
            }
            
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid != null && !uuid.trim().isEmpty()) {
                    return uuid.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID by ID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return null;
    }
    
    /**
     * Get patient UUID by person ID
     */
    public static String getPatientUuidByPersonId(String personId) {
        if (personId == null || personId.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            String sql = "SELECT uuid FROM clinlims.patient WHERE person_id = ?";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, Integer.parseInt(personId));
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid != null && !uuid.trim().isEmpty()) {
                    return uuid.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID by person ID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return null;
    }
    
    /**
     * Get patient UUID from sample UUID
     */
    public static String getPatientUuidBySampleUuid(String sampleUuid) {
        if (sampleUuid == null || sampleUuid.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            String sql = "SELECT p.uuid FROM clinlims.patient p " +
                        "INNER JOIN clinlims.sample_human sh ON sh.patient_id = p.id " +
                        "INNER JOIN clinlims.sample s ON s.id = sh.samp_id " +
                        "WHERE s.uuid = ? LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, sampleUuid);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid != null && !uuid.trim().isEmpty()) {
                    return uuid.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID by sample UUID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return null;
    }
    
    /**
     * Get patient UUID from accession number
     */
    public static String getPatientUuidByAccessionNumber(String accessionNumber) {
        if (accessionNumber == null || accessionNumber.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            
            String sql = "SELECT p.uuid FROM clinlims.patient p " +
                        "INNER JOIN clinlims.sample_human sh ON sh.patient_id = p.id " +
                        "INNER JOIN clinlims.sample s ON s.id = sh.samp_id " +
                        "WHERE s.accession_number = ? " +
                        "ORDER BY s.id DESC LIMIT 1";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, accessionNumber);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid != null && !uuid.trim().isEmpty()) {
                    return uuid.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting patient UUID by accession number: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return null;
    }
    
    /**
     * Get patient ID by UUID
     */
    public static String getPatientIdByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            String sql = "SELECT id FROM clinlims.patient WHERE uuid = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, uuid);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return String.valueOf(rs.getInt("id"));
            }
        } catch (Exception e) {
            System.err.println("Error getting patient ID by UUID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return null;
    }
    
    /**
     * Check if patient UUID exists
     */
    public static boolean patientUuidExists(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = HibernateUtil.getSession().connection();
            String sql = "SELECT 1 FROM clinlims.patient WHERE uuid = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, uuid);
            rs = ps.executeQuery();
            
            return rs.next();
        } catch (Exception e) {
            System.err.println("Error checking patient UUID existence: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeResources(rs, ps);
        }
        
        return false;
    }
    
    /**
     * Helper method to close database resources
     */
    private static void closeResources(ResultSet rs, PreparedStatement ps) {
        try {
            if (rs != null) rs.close();
        } catch (Exception e) {
            System.err.println("Error closing ResultSet: " + e.getMessage());
        }
        
        try {
            if (ps != null) ps.close();
        } catch (Exception e) {
            System.err.println("Error closing PreparedStatement: " + e.getMessage());
        }
    }
}