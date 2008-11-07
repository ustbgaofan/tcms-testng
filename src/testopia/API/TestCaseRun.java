 /*
  * The contents of this file are subject to the Mozilla Public
  * License Version 1.1 (the "License"); you may not use this file
  * except in compliance with the License. You may obtain a copy of
  * the License at http://www.mozilla.org/MPL/
  *
  * Software distributed under the License is distributed on an "AS
  * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
  * implied. See the License for the specific language governing
  * rights and limitations under the License.
  *
  * The Original Code is the Bugzilla Testopia Java API.
  *
  * The Initial Developer of the Original Code is Andrew Nelson.
  * Portions created by Andrew Nelson are Copyright (C) 2006
  * Novell. All Rights Reserved.
  *
  * Contributor(s): Andrew Nelson <anelson@novell.com>
  *
  */
package testopia.API;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;

public class TestCaseRun extends TestopiaObject{
	
	public enum Statuses {IDLE, PASSED, FAILED, RUNNING, PAUSED, BLOCKED, ERROR};


	//inputed values to get a testRun
	private Integer runID;
	
	//variables used to update the testRun
	private StringAttribute notes = newStringAttribute("notes", null);
	private StringAttribute summary = newStringAttribute("summary", null);
	private StringAttribute build = newStringAttribute("build", null);  
	private IntegerAttribute case_id = newIntegerAttribute("case_id", null);  
	private StringAttribute environment = newStringAttribute("environment", null); 
	private StringAttribute status = newStringAttribute("status", null); 
	
	/**
	 * 
	 * @param userName your bugzilla/testopia userName
	 * @param password your password 
	 * @param url the url of the testopia server
	 * @param runID - Integer the runID, you may enter null here if you are creating a test run
	 */
	public TestCaseRun(Session session, Integer runID)
	{
		this.session = session;
		this.runID = runID; 
		this.listMethod = "TestRun.list";
		
		this.cleanAllAttributes();
	}
	public TestCaseRun(Session session, Integer case_id, String build, String environment)
	{
		this.session = session;
		this.case_id.set(case_id);
		this.build.set(build);
		this.environment.set(environment);
		this.listMethod = "TestRun.list";
		
		this.cleanAllAttributes();
	}
	
	/**
	 * Updates are not called when the .set is used. You must call update after all your sets
	 * to push the changes over to testopia.
	 * @throws TestopiaException if planID is null 
	 * @throws XmlRpcException
	 * (you made the TestCase with a null caseID and have not created a new test plan)
	 */
	public Map<String,Object> update() throws TestopiaException, XmlRpcException
	{
		if (runID == null) 
			throw new TestopiaException("runID is null.");
		//update the testRunCase
		return super.update("TestRun.update", runID);
	}
	
	/**
	 * Calls the create method with the attributes as-is (as set via contructors
	 * or setters).  
	 * @return a map of the newly created object
	 * @throws XmlRpcException
	 */
	public Map<String,Object> create() throws XmlRpcException{
		this.runID = getNextTestRunID(case_id.get());
		return super.create("TestRun.create");			
	}
	
	private int getNextTestRunID(Integer caseID) throws XmlRpcException{
		Object[] params = new Object[]{caseID};
		Object[] result;
		
		result = (Object[]) session.getClient().execute("Bugzilla.Testopia.Webservice.TestCase.get_case_run_history", params);
		
		int highest = -1;
		for (int i=0;i<result.length;i++){
			Hashtable elem = (Hashtable) result[i];
			int id = (Integer) elem.get("run_id");
			if (id > highest)
			   highest = id;
		}
		return (highest + 1);
	}
	
	/**
	 * Gets the attributes of the test run, runID must not be null
	 * @return a hashMap of all the values found. Returns null if there is an error
	 * and the TestRun cannot be returned
	 * @throws TestopiaException 
	 * @throws XmlRpcException
	 */
	public Map<String, Object> getAttributes() throws TestopiaException, XmlRpcException
	{
		if (runID == null) 
			throw new TestopiaException("runID is null.");
		
		//get the hashmap
		return get("TestRun.get", runID);
	}
	
	
	/**
	 * 
	 * @return an array of objects (Object[]) of all the testcases found. 
	 * Returns null if there is an error and the TestRun cannot be returned
	 * @throws TestopiaException
	 * @throws XmlRpcException
	 */
	public Object[] getTestCases()
	throws TestopiaException, XmlRpcException
	{
		if (runID == null)
			throw new TestopiaException("runID is null.");
		
		return (Object[])this.callXmlrpcMethod("TestRun.get_test_cases",
												runID.intValue());
	}			
		
	/**
	 * 
	 * @return an array of objects (Object[]) of all the testCaseRuns found. 
	 * Returns null if there is an error and the TestRun cannot be found
	 * @throws Exception
	 * @throws XmlRpcException
	 */
	public Object[] getTestCaseRuns()
	throws TestopiaException, XmlRpcException
	{
		if (runID == null) 
			throw new TestopiaException("runID is null.");
			
		return (Object[])this.callXmlrpcMethod("TestRun.get_test_case_runs",
												runID.intValue());
	}
	
	/**
	 * @return the runID
	 */
	public Integer getRunID() {
		return runID;
	}

	/**
	 * @return the notes
	 */
	public String getNotes() {
		return notes.get();
	}

	/**
	 * @return the summary
	 */
	public String getSummary() {
		return summary.get();
	}


	/**
	 * @return the buildID
	 */
	public String getBuild() {
		return build.get();
	}

	/**
	 * @return the environmentID
	 */
	public String getEnvironment() {
		return environment.get();
	}


	/**
	 * 
	 * @param buildID int - the new builID
	 */
	public void setBuild(String build) {
		this.build.set(build);
	}

	/**
	 * 
	 * @param environment int = the new environemnetID
	 */
	public void setEnvironment(String environment) {
		this.environment.set(environment);
	}

	
	/**
	 * 
	 * @param notes String - the new notes 
	 */
	public void setNotes(String notes) {
		this.notes.set(notes);
	}

	
	/**
	 * 
	 * @param summary String - the new summary 
	 */
	public void setSummary(String summary) {
		this.summary.set(summary);
	}
	
	public Integer getCase_id() {
		return case_id.get();
	}
	public void setCase_id(Integer case_id) {
		this.case_id.set(case_id);
	}
	public String getStatus() {
		return status.get();
	}
	public void setStatus(Statuses status) {
		this.status.set(status.toString());
	}
	

}
