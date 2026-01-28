package us.mn.state.health.lims.patient.valueholder;

import java.io.Serializable;

/**
 * Data Transfer Object for Patient information
 * Used to pass patient data between layers including UUID
 */
public class PatientDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String patientId;
    private String patientUUID;
    private String firstName;
    private String lastName;
    private String gender;
    private String dateOfBirth;
    private String nationalId;
    private String personId;
    
    public PatientDTO() {
    }
    
    public PatientDTO(String patientId, String patientUUID) {
        this.patientId = patientId;
        this.patientUUID = patientUUID;
    }
    
    // Getters and Setters
    
    public String getPatientId() {
        return patientId;
    }
    
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    
    public String getPatientUUID() {
        return patientUUID;
    }
    
    public void setPatientUUID(String patientUUID) {
        this.patientUUID = patientUUID;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getGender() {
        return gender;
    }
    
    public void setGender(String gender) {
        this.gender = gender;
    }
    
    public String getDateOfBirth() {
        return dateOfBirth;
    }
    
    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
    
    public String getNationalId() {
        return nationalId;
    }
    
    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }
    
    public String getPersonId() {
        return personId;
    }
    
    public void setPersonId(String personId) {
        this.personId = personId;
    }
    
    @Override
    public String toString() {
        return "PatientDTO{" +
                "patientId='" + patientId + '\'' +
                ", patientUUID='" + patientUUID + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", gender='" + gender + '\'' +
                ", dateOfBirth='" + dateOfBirth + '\'' +
                '}';
    }
}