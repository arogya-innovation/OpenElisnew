<%@page import="us.mn.state.health.lims.common.formfields.FormFields.Field"%>
<%@ page language="java" contentType="text/html; charset=utf-8" %>
<%@ page import="us.mn.state.health.lims.common.action.IActionConstants,
			     us.mn.state.health.lims.common.util.SystemConfiguration,
			     us.mn.state.health.lims.common.util.ConfigurationProperties,
			     us.mn.state.health.lims.common.util.ConfigurationProperties.Property,
			     us.mn.state.health.lims.common.provider.validation.AccessionNumberValidatorFactory,
			     us.mn.state.health.lims.common.provider.validation.IAccessionNumberValidator,
			     us.mn.state.health.lims.common.formfields.FormFields,
                 us.mn.state.health.lims.common.util.Versioning,
			     us.mn.state.health.lims.common.util.StringUtil,
			     us.mn.state.health.lims.common.util.IdValuePair,
			     us.mn.state.health.lims.payment.service.PaymentValidationService.InvoiceInfo" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>

<%@ taglib uri="/tags/struts-bean"		prefix="bean" %>
<%@ taglib uri="/tags/struts-html"		prefix="html" %>
<%@ taglib uri="/tags/struts-logic"		prefix="logic" %>
<%@ taglib uri="/tags/labdev-view"		prefix="app" %>
<%@ taglib uri="/tags/struts-tiles"     prefix="tiles" %>
<%@ taglib uri="/tags/sourceforge-ajax" prefix="ajax"%>

<bean:define id="formName"		value='<%=(String) request.getAttribute(IActionConstants.FORM_NAME)%>' />
<bean:define id="idSeparator"	value='<%=SystemConfiguration.getInstance().getDefaultIdSeparator()%>' />
<bean:define id="accessionFormat" value='<%= ConfigurationProperties.getInstance().getPropertyValue(Property.AccessionFormat)%>' />
<bean:define id="genericDomain" value='' />
<bean:define id="fieldsetOrder" name='<%=formName%>' property='sampleEntryFieldsetOrder' type="java.util.List" />

<%!
	String basePath = "";
	boolean useSTNumber = true;
	boolean useMothersName = true;
	boolean useReferralSiteList = false;
	boolean useProviderInfo = false;
	boolean patientRequired = false;
	boolean trackPayment = false;
	boolean requesterLastNameRequired = false;
    boolean useSampleSource = false;
	IAccessionNumberValidator accessionNumberValidator;
    Map<String,String> fieldsetToJspMap = new HashMap<String, String>() ;
	String sampleId = "";

%>
<%
	String path = request.getContextPath();
	basePath = path + "/";
	useSTNumber =  FormFields.getInstance().useField(FormFields.Field.StNumber);
	useMothersName = FormFields.getInstance().useField(FormFields.Field.MothersName);
	useReferralSiteList = FormFields.getInstance().useField(FormFields.Field.RequesterSiteList);
	useProviderInfo = FormFields.getInstance().useField(FormFields.Field.ProviderInfo);
	patientRequired = FormFields.getInstance().useField(FormFields.Field.PatientRequired);
	trackPayment = ConfigurationProperties.getInstance().isPropertyValueEqual(Property.trackPatientPayment, "true");
	accessionNumberValidator = new AccessionNumberValidatorFactory().getValidator();
	requesterLastNameRequired = FormFields.getInstance().useField(Field.SampleEntryRequesterLastNameRequired);
    useSampleSource = FormFields.getInstance().useField(Field.UseSampleSource);
    fieldsetToJspMap.put("patient","SamplePatientInfoSection.jsp");
    fieldsetToJspMap.put("samples","SamplePatientSampleSection.jsp");
    fieldsetToJspMap.put("order","SampleOrderInfoSection.jsp");
	sampleId = request.getParameter("id");
	
	// Payment validation check - Updated for patient-based API
	boolean paymentBlocked = "true".equals(request.getAttribute("paymentBlocked"));
	String paymentStatus = (String) request.getAttribute("paymentStatus");
	String paymentMessage = (String) request.getAttribute("paymentMessage");
	String patientUuid = (String) request.getAttribute("patientUuid");
	String paymentPatientName = (String) request.getAttribute("paymentPatientName");
	String paymentPatientRef = (String) request.getAttribute("paymentPatientRef");
	Double paymentTotalDue = (Double) request.getAttribute("paymentTotalDue");
	Integer paymentInvoiceCount = (Integer) request.getAttribute("paymentInvoiceCount");
	List<InvoiceInfo> paymentInvoices = (List<InvoiceInfo>) request.getAttribute("paymentInvoices");
%>

<script type="text/javascript" src="<%=basePath%>scripts/utilities.js?ver=<%= Versioning.getBuildNumber() %>" ></script>

<link rel="stylesheet" href="css/jquery_ui/jquery.ui.all.css?ver=<%= Versioning.getBuildNumber() %>">
<link rel="stylesheet" href="css/customAutocomplete.css?ver=<%= Versioning.getBuildNumber() %>">

<script src="scripts/ui/jquery.ui.core.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script src="scripts/ui/jquery.ui.widget.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script src="scripts/ui/jquery.ui.button.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script src="scripts/ui/jquery.ui.position.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script src="scripts/ui/jquery.ui.autocomplete.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script src="scripts/customAutocomplete.js?ver=<%= Versioning.getBuildNumber() %>"></script>
<script type="text/javascript" src="scripts/ajaxCalls.js?ver=<%= Versioning.getBuildNumber() %>"></script>

<!-- Payment Warning Modal - Enhanced for Patient Payment Status -->
<% if (paymentBlocked) { %>
<style>
.payment-warning-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.85);
    z-index: 10000;
    display: flex;
    align-items: center;
    justify-content: center;
}
.payment-warning-modal {
    background: white;
    padding: 30px;
    border-radius: 10px;
    max-width: 600px;
    width: 90%;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
    z-index: 10001;
}
.warning-header {
    text-align: center;
    margin-bottom: 25px;
    color: #d9534f;
    border-bottom: 3px solid #d9534f;
    padding-bottom: 15px;
}
.warning-icon {
    font-size: 60px;
    display: block;
    margin-bottom: 10px;
    animation: pulse 2s infinite;
}
@keyframes pulse {
    0%, 100% { transform: scale(1); }
    50% { transform: scale(1.1); }
}
.payment-details {
    background: #fff3cd;
    padding: 20px;
    margin: 20px 0;
    border-left: 5px solid #ffc107;
    border-radius: 4px;
}
.payment-details h3 {
    margin-top: 0;
    color: #856404;
    font-size: 18px;
}
.patient-info {
    background: #f8f9fa;
    padding: 15px;
    margin: 15px 0;
    border-left: 4px solid #007bff;
    border-radius: 4px;
}
.patient-info .info-row {
    display: flex;
    justify-content: space-between;
    margin: 8px 0;
    padding: 5px 0;
    border-bottom: 1px solid #dee2e6;
}
.patient-info .info-label {
    font-weight: bold;
    color: #495057;
}
.patient-info .info-value {
    color: #212529;
}
.invoice-list {
    margin: 15px 0;
    max-height: 250px;
    overflow-y: auto;
}
.invoice-item {
    background: white;
    padding: 12px;
    margin: 8px 0;
    border: 1px solid #dee2e6;
    border-radius: 4px;
    border-left: 3px solid #dc3545;
}
.invoice-item .invoice-header {
    font-weight: bold;
    color: #dc3545;
    margin-bottom: 5px;
}
.invoice-item .invoice-detail {
    font-size: 13px;
    color: #6c757d;
    margin: 3px 0;
}
.total-due {
    background: #dc3545;
    color: white;
    padding: 15px;
    margin: 20px 0;
    border-radius: 6px;
    text-align: center;
    font-size: 20px;
    font-weight: bold;
}
.warning-message {
    text-align: center;
    padding: 20px;
    background: #f8d7da;
    color: #721c24;
    border: 1px solid #f5c6cb;
    border-radius: 6px;
    margin: 20px 0;
    font-weight: 500;
}
.warning-actions {
    text-align: center;
    margin-top: 25px;
    padding-top: 20px;
    border-top: 2px solid #dee2e6;
}
.warning-actions button {
    padding: 12px 24px;
    margin: 5px;
    border: none;
    border-radius: 5px;
    cursor: pointer;
    font-size: 15px;
    font-weight: 500;
    transition: all 0.3s;
}
.warning-actions button:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}
.btn-primary { 
    background: #007bff; 
    color: white; 
}
.btn-primary:hover { 
    background: #0056b3; 
}
.btn-secondary { 
    background: #6c757d; 
    color: white; 
}
.btn-secondary:hover { 
    background: #545b62; 
}
.btn-info { 
    background: #17a2b8; 
    color: white; 
}
.btn-info:hover { 
    background: #117a8b; 
}
</style>

<div class="payment-warning-overlay">
    <div class="payment-warning-modal">
        <div class="warning-header">
            <span class="warning-icon">⚠️</span>
            <h2>Payment Verification Required</h2>
        </div>
        
        <% if (paymentPatientName != null || paymentPatientRef != null) { %>
        <div class="patient-info">
            <h3 style="margin-top: 0; color: #007bff;">Patient Information</h3>
            <% if (paymentPatientName != null) { %>
            <div class="info-row">
                <span class="info-label">Patient Name:</span>
                <span class="info-value"><%= paymentPatientName %></span>
            </div>
            <% } %>
            <% if (paymentPatientRef != null && !paymentPatientRef.isEmpty()) { %>
            <div class="info-row">
                <span class="info-label">Patient ID:</span>
                <span class="info-value"><%= paymentPatientRef %></span>
            </div>
            <% } %>
            <% if (patientUuid != null) { %>
            <div class="info-row">
                <span class="info-label">Patient UUID:</span>
                <span class="info-value" style="font-size: 11px;"><%= patientUuid %></span>
            </div>
            <% } %>
        </div>
        <% } %>
        
        <div class="payment-details">
            <h3>Payment Status</h3>
            <div class="info-row">
                <span class="info-label">Status:</span>
                <span class="info-value" style="color: #d9534f; font-weight: bold;">
                    <%= paymentStatus != null ? paymentStatus.toUpperCase() : "UNPAID" %>
                </span>
            </div>
            <div class="info-row">
                <span class="info-label">Message:</span>
                <span class="info-value"><%= paymentMessage != null ? paymentMessage : "Payment verification required" %></span>
            </div>
        </div>
        
        <% if (paymentTotalDue != null && paymentTotalDue > 0) { %>
        <div class="total-due">
            Total Amount Due: ₹<%= String.format("%.2f", paymentTotalDue) %>
        </div>
        <% } %>
        
        <% if (paymentInvoices != null && !paymentInvoices.isEmpty()) { %>
        <div style="margin: 20px 0;">
            <h3 style="color: #dc3545; margin-bottom: 10px;">
                Unpaid Invoices (<%= paymentInvoiceCount != null ? paymentInvoiceCount : paymentInvoices.size() %>)
            </h3>
            <div class="invoice-list">
                <% for (InvoiceInfo invoice : paymentInvoices) { %>
                <div class="invoice-item">
                    <div class="invoice-header">
                        <%= invoice.getInvoiceNumber() %>
                        <% if (invoice.getShopName() != null) { %>
                        - <%= invoice.getShopName().toUpperCase() %>
                        <% } %>
                    </div>
                    <div class="invoice-detail">
                        <strong>Date:</strong> <%= invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : "N/A" %>
                    </div>
                    <div class="invoice-detail">
                        <strong>Total Amount:</strong> ₹<%= String.format("%.2f", invoice.getTotalAmount()) %>
                    </div>
                    <div class="invoice-detail">
                        <strong>Amount Due:</strong> 
                        <span style="color: #dc3545; font-weight: bold;">
                            ₹<%= String.format("%.2f", invoice.getAmountDue()) %>
                        </span>
                    </div>
                    <div class="invoice-detail">
                        <strong>Payment State:</strong> <%= invoice.getPaymentState() != null ? invoice.getPaymentState() : "Not Paid" %>
                    </div>
                </div>
                <% } %>
            </div>
        </div>
        <% } %>
        
        <div class="warning-message">
            <strong>⛔ Sample Collection Blocked</strong><br/>
            Sample collection cannot proceed until all pending OPD lab invoices are settled.<br/>
            Please complete payment at the billing counter.
        </div>
        
        <div class="warning-actions">
            <button type="button" class="btn-primary" onclick="window.location.href='<%= basePath %>billing'">
                🏦 Go to Billing
            </button>
            <button type="button" class="btn-info" onclick="window.location.reload()">
                🔄 Refresh Status
            </button>
            <button type="button" class="btn-secondary" onclick="window.history.back()">
                ← Go Back
            </button>
        </div>
    </div>
</div>

<script type="text/javascript">
(function() {
    console.log("Payment blocked - Disabling form elements");
    
    // Disable all form elements except modal buttons
    var elements = document.querySelectorAll('input, select, textarea, button');
    for (var i = 0; i < elements.length; i++) {
        if (!elements[i].closest('.warning-actions')) {
            elements[i].disabled = true;
            elements[i].style.opacity = '0.5';
            elements[i].style.cursor = 'not-allowed';
        }
    }
    
    // Prevent form submission
    var forms = document.querySelectorAll('form');
    for (var i = 0; i < forms.length; i++) {
        forms[i].onsubmit = function(e) {
            e.preventDefault();
            alert('❌ Payment verification required. Please clear pending dues at billing counter.');
            return false;
        };
    }
    
    // Override save function
    window.savePage = function() {
        alert('❌ Payment verification required before saving. Please clear pending dues.');
        return false;
    };
    
    // Prevent any clicks outside modal
    document.querySelector('.payment-warning-overlay').addEventListener('click', function(e) {
        if (e.target === this) {
            alert('⚠️ Please complete payment verification before proceeding.');
        }
    });
    
    // Log payment block reason
    console.log("Payment Status: <%= paymentStatus %>");
    console.log("Payment Message: <%= paymentMessage %>");
    <% if (paymentTotalDue != null) { %>
    console.log("Total Due: <%= paymentTotalDue %>");
    <% } %>
})();
</script>
<% } %>

<script type="text/javascript" >

var useSTNumber = <%= useSTNumber %>;
var useMothersName = <%= useMothersName %>;
var useReferralSiteList = <%= useReferralSiteList%>;
var requesterLastNameRequired = <%= requesterLastNameRequired %>
var useSampleSource = <%= useSampleSource%>
var dirty = false;
var invalidSampleElements = new Array();
var requiredFields = new Array("labNo", "receivedDateForDisplay" );
var sampleId = "<%= sampleId %>";

if( requesterLastNameRequired ){
	requiredFields.push("providerLastNameID");
}
<% if( FormFields.getInstance().useField(Field.SampleEntryUseRequestDate)){ %>
	requiredFields.push("requestDate");
<% } %>
<%  if (requesterLastNameRequired) { %>
	requiredFields.push("providerLastNameID");
<% } %>
<%  if (useSampleSource) { %>
requiredFields.push("sampleSourceID");
<% } %>



$jq(function() {
     	var dropdown = $jq( "select#requesterId" );
        autoCompleteWidth = dropdown.width() + 66 + 'px';
        clearNonMatching = false;
		capitialize = true;
		dropdown.combobox();
       // invalidLabID = '<bean:message key="error.site.invalid"/>'; // Alert if value is typed that's not on list. FIX - add badmessage icon
        maxRepMsg = '<bean:message key="sample.entry.project.siteMaxMsg"/>';

        resultCallBack = function( textValue) {
  				siteListChanged(textValue);
  				makeDirty();
  				setSave();
				};


        $('<%= fieldsetOrder.get(0) +"Display" %>').show();

        $('addEditPatient').hide();
	if(sampleId != "null") {
		getSampleOrderDetailsFromSampleId(sampleId, processSampleOrderDetailsSuccess, processSampleOrderDetailsFailure);
	}
	else{
		getDefaultSampleSource(processDefaultSampleSourceSuccess)
	}

});

function processDefaultSampleSourceSuccess(xhr) {
	var defaultSampleSource = xhr.responseXML.getElementsByTagName("defaultSampleSourceID");
	$jq("#sampleSourceID").val(defaultSampleSource[0].innerHTML);
}

function processSampleOrderDetailsSuccess(xhr){
	var sampleSource = xhr.responseXML.getElementsByTagName("sampleSource");
	var sampleRequester = xhr.responseXML.getElementsByTagName("sampleRequester");
	var sampleReceivedDateForDisplay = xhr.responseXML.getElementsByTagName("sampleReceivedDateForDisplay");
	$jq("#sampleSourceID").val(sampleSource[0].innerHTML);
	$jq("#sampleSourceID").attr("disabled", "disabled");
	$jq("#receivedDateForDisplay").val(sampleReceivedDateForDisplay[0].innerHTML);
	if(sampleRequester.length > 0)
	{
		$jq("#providerId").val(sampleRequester[0].innerHTML);
	}
}
function processSampleOrderDetailsFailure(xhr){
}

function isFieldValid(fieldname)
{
	return invalidSampleElements.indexOf(fieldname) == -1;
}

function setSampleFieldInvalid(field)
{
	if( invalidSampleElements.indexOf(field) == -1 )
	{
		invalidSampleElements.push(field);
	}
}

function setSampleFieldValid(field)
{
	var removeIndex = invalidSampleElements.indexOf( field );
	if( removeIndex != -1 )
	{
		for( var i = removeIndex + 1; i < invalidSampleElements.length; i++ )
		{
			invalidSampleElements[i - 1] = invalidSampleElements[i];
		}

		invalidSampleElements.length--;
	}
}

function isSaveEnabled()
{
	return invalidSampleElements.length == 0;
}

function submitTheForm(form)
{
	setAction(form, 'Update', 'yes', '?ID=');
}

function  /*void*/ processValidateEntryDateSuccess(xhr){

    //alert(xhr.responseText);

	var message = xhr.responseXML.getElementsByTagName("message").item(0).firstChild.nodeValue;
	var formField = xhr.responseXML.getElementsByTagName("formfield").item(0).firstChild.nodeValue;

	var isValid = message == "<%=IActionConstants.VALID%>";

	//utilites.js
	selectFieldErrorDisplay( isValid, $(formField));
	setSampleFieldValidity( isValid, formField );
	setSave();

	if( message == '<%=IActionConstants.INVALID_TO_LARGE%>' ){
		alert( '<bean:message key="error.date.inFuture"/>' );
	}else if( message == '<%=IActionConstants.INVALID_TO_SMALL%>' ){
		alert( '<bean:message key="error.date.inPast"/>' );
	}
}

function successUpdateAccession(xhr)
{

}

function checkValidEntryDate(date, dateRange)
{
	if(!date.value || date.value == ""){
		setSave();
		return;
	}

	if( !dateRange || dateRange == ""){
		dateRange = 'past';
	}
	//ajax call from utilites.js
	isValidDate( date.value, processValidateEntryDateSuccess, date.name, dateRange );
}


function processAccessionSuccess(xhr)
{
	//alert(xhr.responseText);
	var formField = xhr.responseXML.getElementsByTagName("formfield").item(0);
	var message = xhr.responseXML.getElementsByTagName("message").item(0);
	var success = false;

	if (message.firstChild.nodeValue == "valid"){
		success = true;
	}
	var labElement = formField.firstChild.nodeValue;
	selectFieldErrorDisplay( success, $(labElement));
	setSampleFieldValidity( success, labElement);

	if( !success ){
		alert( message.firstChild.nodeValue );
	}

	setSave();
}

function processAccessionFailure(xhr)
{
	//unhandled error: someday we should be nicer to the user
}


function checkAccessionNumber( accessionNumber )
{
    if ( !new RegExp("^([0-9]+)([-]*)([0-9]*)$").test(accessionNumber.value) ) {
        setSampleFieldInvalid(accessionNumber.name );
       	setValidIndicaterOnField(false, accessionNumber.name);
    }
	//check if empty
	else if ( !fieldIsEmptyById( "labNo" ) )
	{
		validateAccessionNumberOnServer(false, accessionNumber.id, accessionNumber.value, processAccessionSuccess, processAccessionFailure );
	}
	else
	{
		setSampleFieldInvalid(accessionNumber.name );
		setValidIndicaterOnField(false, accessionNumber.name);
	}

	setSave();
}

//Note this is hard wired for Haiti -- do not use
function checkEntryPhoneNumber( phone )
{

	var regEx = new RegExp("^\\(?\\d{3}\\)?\\s?\\d{4}[- ]?\\d{4}\\s*$");

	var valid = regEx.test(phone.value);

	selectFieldErrorDisplay( valid, phone );
	setValidIndicaterOnField(valid, phone.name);

	setSave();
}

function setSampleFieldValidity( valid, fieldName ){

	if( valid )
	{
		setSampleFieldValid(fieldName);
	}
	else
	{
		setSampleFieldInvalid(fieldName);
	}
}


function checkValidTime(time)
{
	var lowRangeRegEx = new RegExp("^[0-1]{0,1}\\d:[0-5]\\d$");
	var highRangeRegEx = new RegExp("^2[0-3]:[0-5]\\d$");

	if( lowRangeRegEx.test(time.value) ||
	    highRangeRegEx.test(time.value) )
	{
		if( time.value.length == 4 )
		{
			time.value = "0" + time.value;
		}
		clearFieldErrorDisplay(time);
		setSampleFieldValid(time.name);
	}
	else
	{
		setFieldErrorDisplay(time);
		setSampleFieldInvalid(time.name);
	}

	setSave();
}

function setMyCancelAction(form, action, validate, parameters)
{
	//first turn off any further validation
	setAction(window.document.forms[0], 'Cancel', 'no', '');
}


function patientInfoValid()
{
	var hasError = false;
	var returnMessage = "";

	if( fieldIsEmptyById("patientID") )
	{
		hasError = true;
		returnMessage += ": patient ID";
	}

	if( fieldIsEmptyById("dossierID") )
	{
		hasError = true;
		returnMessage += ": dossier ID";
	}

	if( fieldIsEmptyById("firstNameID") )
	{
		hasError = true;
		returnMessage += ": first Name";
	}
	if( fieldIsEmptyById("lastNameID") )
	{
		hasError = true;
		returnMessage += ": last Name";
	}


	if( hasError )
	{
		returnMessage = "Please enter the following patient values  " + returnMessage;
	}else
	{
		returnMessage = "valid";
	}

	return returnMessage;
}



function saveItToParentForm(form) {
 submitTheForm(form);
}

function getNextAccessionNumber() {
	generateNextScanNumber();
}

function generateNextScanNumber(){

	var selected = "";

	new Ajax.Request (
                          'ajaxQueryXML',  //url
                           {//options
                             method: 'get', //http method
                             parameters: "provider=SampleEntryGenerateScanProvider&programCode=" + selected,
                             //indicator: 'throbbing'
                             onSuccess:  processScanSuccess,
                             onFailure:  processScanFailure
                           }
                          );
}

function processScanSuccess(xhr){
	//alert(xhr.responseText);
	var formField = xhr.responseXML.getElementsByTagName("formfield").item(0);
	var returnedData = formField.firstChild.nodeValue;

	var message = xhr.responseXML.getElementsByTagName("message").item(0);

	var success = message.firstChild.nodeValue == "valid";

	if( success ){
		$("labNo").value = returnedData;

	}else{
		alert( "<%= StringUtil.getMessageForKey("error.accession.no.next") %>");
		$("labNo").value = "";
	}

	var targetName = $("labNo").name;
	selectFieldErrorDisplay(success, $(targetName));
	setValidIndicaterOnField( success, targetName );

	setSave();
}

function processScanFailure(xhr){
	//some user friendly response needs to be given to the user
}

function addPatientInfo(  ){
	$("patientDisplay").show();
}

function showHideSection(button, targetId){
	if( button.value == "+" ){
		$(targetId).show();
		button.value = "-";
	}else{
		$(targetId).hide();
		button.value = "+";
	}
}

function /*bool*/ requiredSampleEntryFieldsValid(){
	for( var i = 0; i < requiredFields.length; ++i ){
		if( $(requiredFields[i]).value.blank() ){
			//special casing
			if( requiredFields[i] == "requesterId" &&
			   !( ($("requesterId").selectedIndex == 0)  &&  $("newRequesterName").value.blank())){
				continue;
			}
		return false;
		}
	}


	return allSamplesHaveTests();
}

function /*bool*/ sampleEntryTopValid(){
	return invalidSampleElements.length == 0 && requiredSampleEntryFieldsValid();
}

function /*void*/ loadSamples(){
	alert( "Implementation error:  loadSamples not found in addSample tile");
}

function show(id){
	document.getElementById(id).style.visibility="visible";
}

function hide(id){
	document.getElementById(id).style.visibility="hidden";
}

function orderTypeSelected( radioElement){
	labOrderType = radioElement.value; //labOrderType is in sampleAdd.jsp
	if( removeAllRows){
		removeAllRows();
	}
	//this is bogus, we should go back to the server to load the dropdown
	if( radioElement.value == 2){
		$("followupLabOrderPeriodId").show();
		$("initialLabOrderPeriodId").hide();
	}else{
		$("initialLabOrderPeriodId").show();
		$("followupLabOrderPeriodId").hide();
	}
	//$("sampleEntryPage").show();
}
function labPeriodChanged( labOrderPeriodElement){
	if( labOrderPeriodElement.length - 1 ==  labOrderPeriodElement.selectedIndex  ){
		$("labOrderPeriodOtherId").show();
	}else{
		$("labOrderPeriodOtherId").hide();
		$("labOrderPeriodOtherId").value = "";
	}
}

function siteListChanged(textValue){
	var siteList = $("requesterId");

	//if the index is 0 it is a new entry, if it is not then the textValue may include the index value
	if( siteList.selectedIndex == 0 || siteList.options[siteList.selectedIndex].label != textValue){
		  $("newRequesterName").value = textValue;
	}else{
		//do auto fill stuff
	}
}

function capitalizeValue( text){
	$("requesterId").value = text.toUpperCase();
}
</script>

<bean:define id="orderTypeList"  name='<%=formName%>' property="orderTypes" type="java.util.Collection"/>
<html:hidden property="currentDate" name="<%=formName%>" styleId="currentDate"/>
<html:hidden property="domain" name="<%=formName%>" value="<%=genericDomain%>" styleId="domain"/>
<html:hidden property="removedSampleItem" value="" styleId="removedSampleItem"/>
<html:hidden property="newRequesterName" name='<%=formName %>' styleId="newRequesterName" />
<div id=sampleEntryPage <%= (orderTypeList == null || orderTypeList.size() == 0)? "" : "style='display:none'"  %>>
    <jsp:include page="<%=fieldsetToJspMap.get(fieldsetOrder.get(0))%>" />
    <jsp:include page="<%=fieldsetToJspMap.get(fieldsetOrder.get(1))%>" />

    <jsp:include page="<%=fieldsetToJspMap.get(fieldsetOrder.get(2))%>" />
</div>
<script type="text/javascript" >

//all methods here either overwrite methods in tiles or all called after they are loaded

function /*void*/ makeDirty(){
	dirty=true;
	if( typeof(showSuccessMessage) != 'undefinded' ){
		showSuccessMessage(false); //refers to last save
	}
	// Adds warning when leaving page if content has been entered into makeDirty form fields
	function formWarning(){
    return "<bean:message key="banner.menu.dataLossWarning"/>";
	}
	window.onbeforeunload = formWarning;
}

function  /*void*/ savePage()
{
    jQuery("#saveButtonId").attr("disabled", "disabled");
	loadSamples(); //in addSample tile
    window.onbeforeunload = null; // Added to flag that formWarning alert isn't needed.
    var form = window.document.forms[0];
	if (supportSTNumber) {
		form.elements.namedItem("patientProperties.STnumber").value = $('ST_ID').value;
	}
	if(sampleId != "null"){
		var accessionNumber = $("labNo").value;
		var collectionDate = $("receivedDateForDisplay").value;
		var rows = $jq('#samplesAddedTable tr');
		var testIdsSelected = new Object();
		var typeIdsSelected = new Object();
		for (var i=1; i<rows.length; i++){
			testIdsSelected[i-1]=$jq('#testIds'+rows[i].id).val();
			typeIdsSelected[i-1] =$jq('#typeId'+rows[i].id).val();
		}
		var testsAndTypes = new Object();
		testsAndTypes.tests = testIdsSelected;
		testsAndTypes.types = typeIdsSelected;
		var typeAndTestIdsJson = JSON.stringify(testsAndTypes);

		updateTestsWithAccessionNumber(accessionNumber, sampleId, collectionDate,typeAndTestIdsJson, successUpdateAccession, processScanFailure)
		form.action = "LabDashboard.do?";
	}else {
		form.action = "SamplePatientEntrySave.do";
	}
	form.submit();
}


function /*void*/ setSave()
{
	var validToSave =  patientFormValid() && sampleEntryTopValid();
    if (validToSave) {
        jQuery("#saveButtonId").removeAttr("disabled", "disabled");
    } else {
        jQuery("#saveButtonId").attr("disabled", "disabled");
    }
}

//called from patientSearch.jsp
function /*void*/ selectedPatientChangedForSample(firstName, lastName, gender, DOB, stNumber, subjectNumb, nationalID, mother, pk ){
	patientInfoChangedForSample( firstName, lastName, gender, DOB, stNumber, subjectNumb, nationalID, mother, pk );
	$("patientPK").value = pk;

	setSave();
}

//called from patientManagment.jsp
function /*void*/ patientInfoChangedForSample( firstName, lastName, gender, DOB, stNumber, subjectNum, nationalID, mother, pk ){
	$("patientPK").value = pk;

	makeDirty();
	setSave();
}

//overwrites function from patient search
function /*void*/ doSelectPatient(){
/*	$("firstName").firstChild.firstChild.nodeValue = currentPatient["first"];
	$("mother").firstChild.firstChild.nodeValue = currentPatient["mother"];
	$("st").firstChild.firstChild.nodeValue = currentPatient["st"];
	$("lastName").firstChild.firstChild.nodeValue = currentPatient["last"];
	$("dob").firstChild.firstChild.nodeValue = currentPatient["DOB"];
	$("national").firstChild.firstChild.nodeValue = currentPatient["national"];
	$("gender").firstChild.firstChild.nodeValue = currentPatient["gender"];
	$("patientPK").value = currentPatient["pk"];

	setSave();

*/
}

var patientRegistered = false;
var sampleRegistered = false;

/* is registered in patientManagement.jsp */
function /*void*/ registerPatientChangedForSampleEntry(){
	if( !patientRegistered ){
		addPatientInfoChangedListener( patientInfoChangedForSample );
		patientRegistered = true;
	}
}

/* is registered in sampleAdd.jsp */
function /*void*/ registerSampleChangedForSampleEntry(){
	if( !sampleRegistered ){
		addSampleChangedListener( makeDirty );
		sampleRegistered = true;
	}
}

registerPatientChangedForSampleEntry();
registerSampleChangedForSampleEntry();

</script>
