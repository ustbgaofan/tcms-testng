/**
 * 
 */
package com.redhat.qe.auto.tcms;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.internal.IResultListener;

import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.jul.AbstractTestProcedureHandler;

import tcms.API.Build;
import tcms.API.Environment;
import tcms.API.Product;
import com.redhat.qe.xmlrpc.Session;
import tcms.API.TestCase;
import tcms.API.TestCaseRun;
import tcms.API.TestPlan;
import tcms.API.TestRun;
import tcms.API.TestopiaException;
import tcms.API.User;

/**
 * @author jweiss
 *
 */
public class TCMSTestNGListener implements IResultListener, ISuiteListener {

	protected static final String TESTNG_COMPONENT_MARKER = "component-";
	protected static final String TESTNG_TESTPLAN_MARKER = "testplan-";
	protected static String TESTOPIA_PW = "";
	protected static String TESTOPIA_USER = "";
	protected static String TESTOPIA_URL = "";
	protected static String TESTOPIA_TESTRUN_TESTPLAN = "";
	protected static String TESTOPIA_TESTRUN_PRODUCT = "";
	protected static String sProcedurePreText = null;
	protected static Environment env = null;
	
	protected static Logger log = Logger.getLogger(TCMSTestNGListener.class.getName());
	protected TestRun testrun;
	protected Product product;
	protected Build build;
	//protected Environment environment;
	protected TestPlan testplan;
	protected TestCase testcase;
	protected Session session;
	protected TestCaseRun testcaserun = null;
	protected static String buildName = "";
	protected static String environmentName = "";
	protected static String version = null;
	protected static boolean testcaseOverwrite = true;
	protected static String myOverwrite = null;
	
	protected static boolean isInUse = false;
	
	static {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "severe");
	}
	
	
	
	public static void setBuild(String buildName){
		TCMSTestNGListener.buildName = buildName;
	}
	public static void setEnvironment(String environmentName){
		TCMSTestNGListener.environmentName = environmentName;
	}
	public static void setVersion(String version){
		TCMSTestNGListener.version = version;
	}
	public static void setTestcaseOverwrite(boolean overwrite){
		TCMSTestNGListener.testcaseOverwrite = overwrite;
	}
	
	public static boolean isInUse(){
		return isInUse;
	}
	
	
	public void onFinish(ISuite suite) {
		// TODO Auto-generated method stub
	}

	public void onStart(ISuite suite) {
		isInUse = true;
		
		
	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onFinish(org.testng.ITestContext)
	 */
	public void onFinish(ITestContext context) {
		testrun.setStatus(TestRun.Status.Stopped);
		String notes = String.format("RESULTS: %d Passed, %d Failed, %d Skipped",
				                     context.getPassedTests().size(),
				                     context.getFailedTests().size(),
				                     context.getSkippedTests().size());
		testrun.setNotes(notes);
		try{
			testrun.update();
			
		}catch(Exception e){
			throw new TestopiaException(e);
		}

	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onStart(org.testng.ITestContext)
	 */
	public void onStart(ITestContext context) {
		//create new test run
		String testname = context.getName();
		try {
			loginTestopia();
			retrieveContext();
			
			// if set, onStart determine env id that needs to be set
			String sEnvironment = System.getProperty("testopia.testrun.environment");
			int iEnvID = -1;
			if (sEnvironment != null) {
				if (env == null ) {
					String[] saEnv = sEnvironment.split(":");
					try {
						env = new Environment(session, product.getId(), saEnv[0], saEnv[1]);
						iEnvID = env.getValueId();
					} catch(Exception e) {
						throw new TestopiaException(e);
					}
				}
			}
			
			testrun = new TestRun(session, testplan.getId(), iEnvID, build.getId(), session.getUserid(), testname, product.getId(), product.getVersionIDByName(version));
			testrun.create();

			// if set, onStart globally set environment for test run
			if (iEnvID != -1) {
				testrun.applyEnvironmentValue();
			}
			
			// if set, onStart set tags on test run
			String sTags = System.getProperty("testopia.testrun.tags");
			if (sTags != null) {
				Object result = testrun.setTags(sTags);
				if (result != null) {
					System.out.println("Setting tag result: " + result.toString());
				} else {
					System.out.println("Setting tag result: null");
				}
			}

		} catch(Exception e){
			//log.severe("Could not create new test run in testopia!  Aborting!");
			TestopiaException te=new TestopiaException("Could not create new test run in testopia.");
			te.initCause(e);
			throw te;
		}
		
	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onTestFailedButWithinSuccessPercentage(org.testng.ITestResult)
	 */
	public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onTestFailure(org.testng.ITestResult)
	 */
	public void onTestFailure(ITestResult result) {
		//also update the test run
		markTestRunComplete(result);
	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onTestSkipped(org.testng.ITestResult)
	 */
	public void onTestSkipped(ITestResult result) {
		markTestRunComplete(result);

	}

	/**
	 *  Take component names from testng annotation - convention is if you 
	 * mark a test with groups="component-Xyz" then the testcase in testopia
	 * will be created with a component "Xyz" added. If the component
	 * can't be found in testopia, it'll be skipped.  Can add multiple components
	 * in this manner (with multiple groups that start with "component-".  Will also
	 * remove components that are not in the annotation.
	 * -jweiss
	 *
	 * @param result
	 */
	protected void syncComponents(ITestResult result){
		List<String> existingComponents = new ArrayList<String>();
		try {
			Object[] components = testcase.getComponents();
			for (Object component: components){
				String componentName = (String)((Map<String,Object>)component).get("name");
				existingComponents.add(componentName);
			}
		}catch(Exception e){
			log.log(Level.FINER, "Unable to retrieve existing components for testcase " + testcase.getId() + ".", e);
		}
		List<String> newComponents = getComponentsFromGroupAnnotations(result);
		for (String component: newComponents){
			if (existingComponents.contains(component)) {
				log.finer("Component is already in testcase.");
			}
			else {
				try {
					Integer componentID = product.getComponentIDByName(component, TESTOPIA_TESTRUN_PRODUCT);
					testcase.addComponent(componentID);
				}
				catch(Exception e){
					log.log(Level.FINER, "Unable to add component '" + component + "' in product '" +
							TESTOPIA_TESTRUN_PRODUCT + "' to testcase.", e);
					continue;
				}
			}
		}
		
		//remove old components
		for (String component: existingComponents){
			if (!newComponents.contains(component)){
				try {
					Integer componentID = product.getComponentIDByName(component, TESTOPIA_TESTRUN_PRODUCT);
					testcase.removeComponent(componentID);
				}
				catch(Exception e){
					log.log(Level.FINER, "Unable to remove component '" + component + "' in product '" +
							TESTOPIA_TESTRUN_PRODUCT + "' to testcase.", e);
					continue;
				}
			}
		}
	}

	protected void syncTestPlans(ITestResult result){
		List<Integer> existingTestPlans = new ArrayList<Integer>();
		try {
			Object[] testplans = testcase.getTestPlans();
			for (Object testplan: testplans){
				String testPlanName = (String)((Map<String,Object>)testplan).get("name");
				existingTestPlans.add(new TestPlan(session, product.getId(), testPlanName, version).getId());
			}
		}
		catch(Exception e){
			log.log(Level.FINER, "Unable to retrieve associated test plans for testcase " + testcase.getId() + ".", e);
		}
		List<Integer> newTestPlans = getTestPlansFromGroupAnnotations(result);
		for (Integer testplan: newTestPlans){
			if (existingTestPlans.contains(testplan))
				log.finer("Testcase is already assigned to test plan.");
			else {
				try {
					testcase.addTestPlan(testplan);
				}
				catch (Exception e){
					log.log(Level.FINER, "Unable to add test plan '" + testplan + "' in product '" +
							TESTOPIA_TESTRUN_PRODUCT + "' to testcase.", e);
					continue;
				}
			}
		}
		
		//remove old test plans
		for (Integer testplan: existingTestPlans){
			if (!newTestPlans.contains(testplan)){
				try {
					testcase.removeTestPlan(testplan);
				}
				catch(Exception e){
					log.log(Level.FINER, "Unable to remove test plan '" + testplan + "' in product '" +
							TESTOPIA_TESTRUN_PRODUCT + "' to testcase.", e);
					continue;
				}
			}
		}
	}
	
	private List<String> getComponentsFromGroupAnnotations(ITestResult result){
		List<String> groups = Arrays.asList(result.getMethod().getGroups());
		List<String> components= new ArrayList<String>();
		for (String group: groups){
			if (group.startsWith(TESTNG_COMPONENT_MARKER)) {
				String component = group.split(TESTNG_COMPONENT_MARKER)[1];
				log.finer("Found component: " + component);
				components.add(component);		
			}
		}
		return components;
	}
	
	protected List<Integer> getTestPlansFromGroupAnnotations(ITestResult result){
		List<String> groups = Arrays.asList(result.getMethod().getGroups());
		List<Integer> testplans= new ArrayList<Integer>();
		for (String group: groups){
			if (group.startsWith(TESTNG_TESTPLAN_MARKER)) {
				String testplan = group.split(TESTNG_TESTPLAN_MARKER)[1];
				log.finer("Found test plan: " + testplan);
				try{
					Integer testplanid = new TestPlan(session, product.getId(), testplan, version).getId();
					log.finer("with plan ID: " + testplanid);
					testplans.add(testplanid);
				}
				catch(Exception e){
					log.finer("Test plan \"" + testplan + "\" not found on Testopia, skipping...");
					continue;
				}
			}
		}
		return testplans;
	}
	
	protected String getPackagelessTestClass(ITestResult result){
		String pkg_class = result.getTestClass().getName();
		log.finest("Got test class of " + pkg_class);
		String[] pkgs=  pkg_class.split("\\.");
		return pkgs[pkgs.length-1];
	}

	protected TestCase getTestCaseByAnnotation(ITestResult result){
		if (result.getMethod().getInvocationCount() > 1) 
			throw new RuntimeException("Cannot overwrite TCMS testcase from method that will be invoked multiple times: " + result.getMethod().getMethodName());
		ImplementsNitrateTest annotation = result.getMethod().getMethod().getAnnotation(ImplementsNitrateTest.class);
		if (annotation == null) throw new RuntimeException("No TCMS annotation present.");
		return new TestCase(session, annotation.caseId());
	}
	
	// Remove some messages that don't belong in the tcms test case procedures
	private String cleanUpLog(String sLogText) {
		String sFinalString = "";
		String[] array = sLogText.split("<br>");
		for( int i = 0; i < array.length; i++) {
			if (!array[i].startsWith("System property automation.propertiesfile is not set") &&
				!array[i].startsWith("\nRunning against") &&
				!array[i].startsWith("\nStarting TestNG Suite") &&
				!array[i].startsWith("\nCurrent URL") &&
				!array[i].startsWith("\nStarting TestNG Script") &&
				!array[i].startsWith("\nStarting Test") &&
				!array[i].startsWith("Starting Test") &&
				!array[i].startsWith("\nTest Passed") &&
				!array[i].startsWith("\nTest Skipped") &&
				!array[i].startsWith("\nTest Failed")) {
				
				if (sFinalString.equals("")) {
					sFinalString = array[i];
				} else {	
					sFinalString = sFinalString + "<br>" + array[i];
				}	
			}	
		}
		if (sFinalString.equals("\n")) {
			sFinalString = "";
		}
		return sFinalString; 
	}
	
	
	
	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onTestStart(org.testng.ITestResult)
	 */
	public void onTestStart(ITestResult result) {
		
		// if the first time, capture & cleanup the initial login process of selenium test script
		if (sProcedurePreText == null) {
			sProcedurePreText = AbstractTestProcedureHandler.getActiveLog();
			sProcedurePreText = cleanUpLog(sProcedurePreText);
		}	
		
		// resetting active log to remove messages from previous tests that were bleeding over
		AbstractTestProcedureHandler.resetActiveLog();
		
		// useful kvp when trying to document data provided tests
		String sAppendParmOneToSummary = System.getProperty("testopia.testcase.appendParmOneToSummary");
		if (sAppendParmOneToSummary == null) {
			sAppendParmOneToSummary = "0";
		}
		
		//create new testcaserun
		int iteration = result.getMethod().getCurrentInvocationCount();
		log.finer("Got getCurrentInvocationCount()=" + iteration  + ", total=" + result.getMethod().getInvocationCount());
		String count = "";
		
		String className = getPackagelessTestClass(result);
		
		if (iteration > 0) count = new Integer(iteration+1).toString();
		String alias =  className + "." + result.getMethod().getMethodName() + count;
		String script = className + "." + result.getMethod().getMethodName();
		String description = result.getMethod().getDescription();
		
		String summary = null;
		if ( (sAppendParmOneToSummary.equals("1")) && (result.getParameters() != null && result.getParameters().length > 0) ) {
			summary = description.length()>0 ? description : script;
			
			int indexToCheck = 0;
			
			// check/skip blockedByBugzillaBug
			if (result.getParameters()[0] instanceof BlockedByBzBug)
				indexToCheck = 1;
			
			if (result.getParameters()[indexToCheck] instanceof String) {
				String parmOne = (String) result.getParameters()[indexToCheck];
				summary = summary + " - " + parmOne;
			} else
				summary = description.length()>0 ? (description + count) : (script + count);
			
		} else
			summary = description.length()>0 ? (description + count) : (script + count);
		
		try {
			try {
				testcase = getTestCaseByAnnotation(result);
			}catch (Exception e){
				log.log(Level.FINER, "Couldn't retrieve testcase via tcms annotation, falling back to retrieving by method name.", e);
				testcase = new TestCase(session, alias);
			}
			//FIXME temporary to fix testcase names
			testcase.setSummary(summary);
			if (result.getParameters() != null && result.getParameters().length > 0) {
				String args =Arrays.deepToString(result.getParameters()); 
				testcase.setArguments(args);
			}
			testcase.update();
			
		}catch(Exception e){
			log.log(Level.FINER, "Testcase retrieval failed on '" + summary + "', probably doesn't exist yet.", e);
			try {
				log.info("Creating new testcase: " + alias);
				testcase = new TestCase(session, "PROPOSED", "--default--", "P1",
						summary, TESTOPIA_TESTRUN_TESTPLAN, TESTOPIA_TESTRUN_PRODUCT, version);
				testcase.setAlias(alias);
				testcase.setIsAutomated(1);
				testcase.create();
			}
			catch(Exception e2){
				throw new TestopiaException(e2);
			}
		}

		syncComponents(result);
		syncTestPlans(result);

		log.finer("Testrun is " + testrun.getId());
		
		testcaserun = new TestCaseRun(session, testrun.getId(), testcase.getId(), build.getId());
		testcaserun.setStatus(TestCaseRun.Statuses.RUNNING);
		try {
			testcaserun.create();
			testrun.addCases(testcaserun.getId());
		}catch(Exception e) {
			throw new TestopiaException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.testng.ITestListener#onTestSuccess(org.testng.ITestResult)
	 */
	public void onTestSuccess(ITestResult result) {
		myOverwrite = System.getProperty("testopia.testcase.overwrite");
		//get the procedure log from the handler
		String action= AbstractTestProcedureHandler.getActiveLog();
		action = cleanUpLog(action);
		action = sProcedurePreText + action;
		if (action == null)
			action = "no procedure found!";
		
		if(myOverwrite.equalsIgnoreCase("1")){
			log.fine("Updating testcase " + testcase.getAlias() + " with successful action log: \n" + action);
			//put it in testopia
		
			testcase.setAction(action);
		
			try {
				testcase.storeText();
				//FIXME remove the following lines later when all records are updated
				testcase.setIsAutomated(1);

				testcase.update();
			}catch(Exception e){
				//throw new TestopiaException(e);
				log.log(Level.WARNING, "Could not update testopia with successful status." + result.getName(), e);
			}
		}
		
		//also update the test run
		markTestRunComplete(result);
	}

	protected void markTestRunComplete(ITestResult result){

		if(testcaserun == null)
			return;
		
		
		if (result.getStatus() == ITestResult.SKIP) testcaserun.setStatus(TestCaseRun.Statuses.BLOCKED);
		else {
						
				//testcaserun.setNotes(throwableToString(result.getThrowable()));		
				//put the whole log instead
				testcaserun.setNotes(AbstractTestProcedureHandler.getActiveLog());
			
			testcaserun.setStatus(result.isSuccess() ? TestCaseRun.Statuses.PASSED : TestCaseRun.Statuses.FAILED);
		}
		
		try {
			testcaserun.update();
		}catch(Exception e){
			throw new TestopiaException(e);
		}finally{
			//reset the handler so that our log for the next testcase run starts fresh.
			AbstractTestProcedureHandler.resetActiveLog();
		}
	}
	
	protected String throwableToString(Throwable t){
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.testng.internal.IConfihttps://testopia.devel.redhat.com/bugzilla/tr_show_plan.cgi?plan_id=425gurationListener#onConfigurationFailure(org.testng.ITestResult)
	 */
	public void onConfigurationFailure(ITestResult result) {
		//markTestRunComplete(result);

	}

	/* (non-Javadoc)
	 * @see org.testng.internal.IConfigurationListener#onConfigurationSkip(org.testng.ITestResult)
	 */
	public void onConfigurationSkip(ITestResult result) {
		//markTestRunComplete(result);

	}

	/* (non-Javadoc)
	 * @see org.testng.internal.IConfigurationListener#onConfigurationSuccess(org.testng.ITestResult)
	 */
	public void onConfigurationSuccess(ITestResult result) {
		//markTestRunComplete(result);

	}
	
	//FIXME this is just temporary for testing
	private static void setLogConfig() throws Exception{
		/*Logger.getLogger("").setLevel(Level.ALL);
		Logger.getLogger("").getHandlers()[0].setFormatter(new ConsoleLogFormatter());
		Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);*/
		LogManager.getLogManager().readConfiguration(new FileInputStream("/home/weissj/log.properties"));
		log.info("Hello");
	}
	
	protected void loginTestopia() throws XmlRpcException, GeneralSecurityException, IOException{
		TESTOPIA_URL = System.getProperty("testopia.url");
		TESTOPIA_USER = System.getProperty("testopia.login");
		TESTOPIA_PW = System.getProperty("testopia.password");
		TESTOPIA_TESTRUN_PRODUCT = System.getProperty("testopia.testrun.product");
		TESTOPIA_TESTRUN_TESTPLAN = System.getProperty("testopia.testrun.testplan");
		log.finer("Logging in to testopia as " + TESTOPIA_USER);
		session = new Session(TESTOPIA_USER, TESTOPIA_PW, new URL(TESTOPIA_URL));
		session.init();
		session.setUserid(new User(session).getId());
		//session.login();
	}
	
	protected void retrieveContext() throws XmlRpcException{
		product = new Product(session, System.getProperty("testopia.testrun.product"));
		testplan = new TestPlan(session, product.getId(), System.getProperty("testopia.testrun.testplan"), version);
		
		
		build = new Build(session, product.getId());
		try {
			build.getBuildIDByName(buildName);
		}
		catch(Exception e){
			log.log(Level.FINER, "Couldn't find build " + buildName + ", creating new.", e);
			build.setName(buildName);
			build.create();
		}
		
		/*HashMap<String,Object> trinst= (HashMap<String, Object>) tr.create();
		TestCaseRun tcr = new TestCaseRun(session,
										  (Integer)trinst.get("run_id"),
										  2948,
										  buildID,
										  envId);
		tcr.create();*/

	}
	
	
	
	public static void main(String args[]) throws Exception{
		
		//System.out.println(Arrays.deepToString(new Object[]{new Integer(4), "hi"}));
		/*setLogConfig();
		log.finer("Testing log setting.");
		/*
		String test = "component-Hi There";
		System.out.println(test.split("component-")[1]);
		
		String pkg_class = "com.jboss.qa.jon20.tests.DynaGroups";
		//log.finer("Got test class of " + pkg_class);
		String[] pkgs=  pkg_class.split("\\.");
		System.out.println("Found class" +  pkgs[pkgs.length-1]);*/
		LogManager.getLogManager().readConfiguration(new FileInputStream("/home/weissj/log.properties"));
		TCMSTestNGListener ttl = new TCMSTestNGListener();
		Properties p = new Properties();
		p.load(new FileInputStream("/home/weissj/automation.properties"));
		for (Object key: p.keySet()){
			System.setProperty((String)key, p.getProperty((String)(key)));
		}
		
		ttl.loginTestopia();
		Product product = new Product(ttl.session, System.getProperty("testopia.testrun.product"));
		TestPlan testplan = new TestPlan(ttl.session, product.getId(), System.getProperty("testopia.testrun.testplan"), "2.4.0-SNAPSHOT");

		System.out.println(product.getId() + "\n" + testplan.getId());
		/*Session session = new Session(TESTOPIA_USER, TESTOPIA_PW, new URL(TESTOPIA_URL));
		session.login();*/

		/*//tc.makeTestCase(id, 0, 0, true, 271, "This is a test of the testy test", 0);
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("summary", "dfdfg");
		Object[] result = new TestopiaTestCase(session, 0).getList(values);
		for (Object res: result){
			System.out.println(res.toString());
		}
		TestCaseRun tcr = new TestCaseRun(session, 2935, 1, 1, 1, 1);
		tcr.makeTestCaseRun(1, 1);
		tcr.setNotes("RICK ASTLEY");
		tcr.setStatus(2);
		tcr.update();*/
		
		/*TestCase tc2 = new TestCase(session, "PROPOSED", "--default--", "P1", "what up dude", "Acceptance", "JBoss ON");
		tc2.setIsAutomated(true);
		tc2.create();
		tc2.setPriorityID("P2");
		tc2.update();
		tc2.update();*/
		
		
		//TestRun tcr = new TestRun(session, 2948, "2.2 CR1", "Windows + Postgres" );
		
		//tcr.create();
		
		//TestCaseRun tcr = new TestCaseRun(session, 2935, )
		/*Product prod = new Product(session);
		Integer prodId = prod.getProductIDByName("JBoss ON");
		TestPlan tp = new TestPlan(session, "Acceptance");
		Integer plan = tp.getId();
		Build bu = new Build(session, prodId);
		Integer build = bu.getBuildIDByName("2.2 CR1");
		Environment env = new Environment(session, prodId, null);
		Integer envId = env.getEnvironemntIDByName("Windows+Postgres");
		TestRun tr = new TestRun(session, plan, envId, build, session.getUserid(), "Test" + System.currentTimeMillis());
		HashMap<String,Object> trinst= (HashMap<String, Object>) tr.create();
		TestCaseRun tcr = new TestCaseRun(session,
										  (Integer)trinst.get("run_id"),
										  2948,
										  build,
										  envId);
		tcr.create();*/
	}

}
