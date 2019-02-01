

import java.awt.RenderingHints.Key;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Result;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;







public class EmailNotification {

	/**
	 * @param args
	 */

	private static final Logger LOG = Logger.getLogger(EmailNotification.class
			.getName());

	
	private static final String EMAIL_PATTERN = 
			"^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
			+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	
	 String validationstatus = null;
	 String envname = null;
	 String mailHost = null;
	 int mailPort = 0;
	 String defaultContact = null;
	 String fromAddress = null;
	// String defaultTestingContact = null;
	 String logLocation = null;
	 
	 private Pattern pattern;
	 private Matcher matcher;

	public EmailNotification(String envipropfilename) {

		logLocation=envipropfilename.substring(0,envipropfilename.lastIndexOf("/"));
				
		Properties envprop = new Properties();

		try {

			envprop.load(new InputStreamReader(new FileInputStream(
					envipropfilename)));

			mailHost = envprop.getProperty("MAIL_HOST");

			mailPort = Integer.parseInt(envprop.getProperty("MAIL_PORT"));

			defaultContact = envprop.getProperty("OPS_ADMIN");

			envname = envprop.getProperty("ENV_NAME");

			fromAddress = envprop.getProperty("FROM_ADDRESS");

			//defaultTestingContact = envprop.getProperty("NOTIFICATION_ID");

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	public EmailNotification(String mailHost, String mailPort, String defaultContact, String envname, String fromAddress, String logLocation) {

		this.logLocation=logLocation;
				
		this.mailHost = mailHost;

		this.mailPort = Integer.parseInt(mailPort);

		this.defaultContact = defaultContact;

		this.envname = envname;

		this.fromAddress = fromAddress;

		//this.defaultTestingContact = defaultTestingContact;
		
		pattern = Pattern.compile(EMAIL_PATTERN);
		
	}
	
	
	public boolean validate(final String hex) {

		matcher = pattern.matcher(hex);
		return matcher.matches();

	}
	
	public String getInvalidJiraIdMsg(String message) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("message", message);

		Template t = ve.getTemplate("InvalidMsg.vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}
	
	public void sendNotification(String templateFile, String dbip,
			String dbsid, String toAddress) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();
		

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);

		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());
		String mesg = writer.toString();

		int errorCode = this.sendEmail(mesg, toAddress, templateFile);

		if (errorCode != 0)
			System.exit(errorCode);

	}

	public String getNotificationMsg(String templateFile, String dbip,
			String dbsid) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);

		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());

		return writer.toString();

	}

	public String getWarningMsg(String templateFile, String dbip, String dbsid,
			String timetaken, String warningfile, String toAddress) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);

		try {

			ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
			Map<String, String> map;

			BufferedReader file = new BufferedReader(new InputStreamReader(
					new FileInputStream(timetaken)));

			String line;

			int totaltime = 0;

			while ((line = file.readLine()) != null) {

				String[] content = line.split("----");

				map = new HashMap<String, String>();

				map.put("scriptName", content[0]);
				map.put("startTime", content[1]);
				map.put("endTime", content[2]);
				map.put("totalTime", content[3]);
				map.put("schemaName", content[4]);
				totaltime = totaltime + Integer.parseInt(content[3]);
				list.add(map);

			}

			ArrayList<Map<String, String>> warnList = new ArrayList<Map<String, String>>();
			Map<String, String> warnMap;

			@SuppressWarnings("resource")
			BufferedReader warnfile = new BufferedReader(new InputStreamReader(
					new FileInputStream(warningfile)));

			String warnline;

			while ((warnline = warnfile.readLine()) != null) {

				String[] content = warnline.split("-/-");

				warnMap = new HashMap<String, String>();

				warnMap.put("schemaName", content[0]);
				warnMap.put("resourceName", content[1]);
				warnMap.put("resourceType", content[2]);
				warnList.add(warnMap);

			}
			context.put("warnlist", warnList);
			context.put("recordList", list);
			context.put("totalTimeTaken", totaltime);
			file.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());

		/**this.sendNotification("notification" + templateFile, dbip, dbsid,
				toAddress);*/
		return writer.toString();

	}

	public String getFailureMsg(String templateFile, String dbip, String dbsid,
			String scriptfile, String schema, String error, String expectedfix,
			String svn, String toAddress) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);
		context.put("scriptfile", scriptfile);
		context.put("dbschema", schema);
		context.put("error", error);
		context.put("fixfile", expectedfix);
		context.put("svnurl", svn);
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());
		/**this.sendNotification("notification" + templateFile, dbip, dbsid,
				toAddress);*/
		return writer.toString();

	}

	public String getSuccessMsg(String templateFile, String release, String product,
			String project, String moduleName, String svnURL, String svnRevision, String builduser, String finalVersion) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		//Template t = ve.getTemplate( "./src/email_html.vm" );

		context.put("release", release);
		context.put("product", product);
		context.put("project", project);
		context.put("moduleName", moduleName);
		context.put("version", finalVersion);
		context.put("revision", svnRevision);
		context.put("svnurl", svnURL);
		context.put("buildUser", builduser);

		//Template t = ve.getTemplate("E:\\jars\\bbbb.vm");
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());
		/**this.sendNotification("notification" + templateFile, dbip, dbsid,
				toAddress);*/
		return writer.toString();

	}

	public String getStartMsg(String templateFile, String dbip, String dbsid,
			String svnurl, String toAddress) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);
		context.put("svnurl", svnurl);
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());
		/**this.sendNotification("notification" + templateFile, dbip, dbsid,
				toAddress);*/
		return writer.toString();
	}

	

	public String getQtpMsg(String templateFile,String environment ,String executedTime, Map<String,String> map)
	{
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("environment",environment);
		context.put("executedtime",executedTime);
		context.put("map", map);
		
		/*List<String> statusList=new ArrayList<String>();
		map = new HashMap<String, String>();
		for(String key:map.keySet())
		{
			statusList.add(key);
			statusList.add(map.get(key));
			
		}
		context.put("statusList", statusList);*/
		
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();
		
		
		
		
	}
	
	
	public String getValidationMsg(String templateFile, String dbip,
			String dbsid, String svnurl, String errorcode, String errordesp,
			String type, String toAddress) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		context.put("envname", envname);
		context.put("databaseip", dbip);
		context.put("databasesid", dbsid);
		validationstatus = type;

		try {

			ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
			Map<String, String> map;

			BufferedReader file = new BufferedReader(new InputStreamReader(this
					.getClass().getResourceAsStream("/errorcode.txt")));

			String line;

			while ((line = file.readLine()) != null) {

				String[] content = line.split("-/-");

				map = new HashMap<String, String>();

				map.put("code", content[1]);
				map.put("descrp", content[2]);
				list.add(map);

			}
			context.put("status", "Fail");
			context.put("errorcode", errorcode);
			context.put("error", errordesp);
			context.put("type", type);
			context.put("svnurl", svnurl);
			context.put("errorList", list);
			file.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		// System.out.println(writer.toString());
		/**this.sendNotification("notification" + templateFile, dbip, dbsid,
				toAddress);*/
		return writer.toString();

	}
	public String getrestartMsg(String templateFile, Map<String,String> map, String environment, String domain, String status) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("environment", environment);
		context.put("domain", domain);
		context.put("status", status);
		context.put("map", map);
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}

	public int sendEmail(String mesg, String toAddress, String sub) {

		try {

			HtmlEmail email = new HtmlEmail();

			String[] e_address = null;

			email.setHostName(mailHost);
			email.setSmtpPort(mailPort);

			
					defaultContact = defaultContact + "," + toAddress;
			
				e_address = defaultContact.split(",");
			

			for (int i = 0; i < e_address.length; ++i) {

				// System.out.println("email address===>" + e_address[i]);

				if ((e_address[i].equals(null) || e_address[i].equals(""))
						|| e_address[i].equals(" "))

				{
					LOG.error("Invalid To address: " + e_address[i]);
					
				} else
					email.addTo(e_address[i]);
			}
			email.setFrom(fromAddress, "Artifact Uploader");

			
				email.setSubject(sub);
			

			email.setHtmlMsg(mesg);
			email.send();

		} catch (EmailException e) {
			System.out.println("Email Sending Failed.");
			e.printStackTrace();
			return 109;
		}
		System.out.println("Email Sending completed.");
		return 0;

	}

	public int sendEmailWithAttachement(String mesg, String toAddress,
			String sub, String sqlfile, String logfile) {

		try {

			HtmlEmail email = new HtmlEmail();

			String[] e_address = null;

			email.setHostName(mailHost);
			email.setSmtpPort(mailPort);

			String opsContact = toAddress.split("--")[0];

			/**String testingContact = toAddress.split("--")[1];

			if (sub.contains("notification")) {

				if (!(testingContact.equals("EMPTY"))) {

					defaultTestingContact = defaultTestingContact + ","
							+ testingContact;
				}
				e_address = defaultTestingContact.split(",");
			} else {*/
				if (!(opsContact.equals("EMPTY"))) {

					defaultContact = defaultContact + "," + opsContact;
				}
				e_address = defaultContact.split(",");
			
			for (int i = 0; i < e_address.length; ++i) {

				// System.out.println("email address===>" + e_address[i]);

				if ((e_address[i].equals(null) || e_address[i].equals(""))
						|| e_address[i].equals(" "))

				{
					LOG.error("Invalid To address: " + e_address[i]);
				} else
					email.addTo(e_address[i]);

			}
			email.setFrom(fromAddress, "CI-ADMIN");

			if (sub.equals("success"))
				email.setSubject(envname
						+ " Automated Script Execution Completed");
			else if (sub.equals("validation"))
				email.setSubject(envname + " Automated Script Execution - "
						+ validationstatus + " Failed");
			else if (sub.equals("failure"))
				email.setSubject(envname + " Automated Script Execution Failed");
			else if (sub.equals("start"))
				email.setSubject(envname
						+ " Automated Script Execution Started");
			else if (sub.equals("resume"))
				email.setSubject(envname
						+ " Automated Script Execution Resumed");
			else if (sub.equals("warning"))
				email.setSubject(envname
						+ " Automated Script Execution completed with warning");
			else {
				LOG.error("Mail Subject error");
				return 109;
			}

			email.setHtmlMsg(mesg);
			File logFile = new File(logfile);
			email.attach(logFile);
			File sqlFile = new File(sqlfile);
			email.attach(sqlFile);
			email.send();

		} catch (EmailException e) {
			System.out.println("Email Sending Failed.");
			e.printStackTrace();
			return 109;
		}

		return 0;
	}
	public String dbtemplate(String templateFile, String serviceName ,String hostName, String port, Map<String,Map<String,String>>  map2)
	{
		
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("serviceName",serviceName);
		context.put("hostName",hostName);
		context.put("port", port);
		context.put("map2", map2);
		int mapSize = map2.size();
		context.put("mapSize", mapSize);
		
		HashMap<String , String> hm = new  HashMap<String , String>();
		hm.put("CM", "CMDS");
		hm.put("NOTES", "NOTESDS");
		hm.put("CENTURY_SHIELD", "AA");
		hm.put("STAGING", "STAGINGDS");
		hm.put("STUB", "STUBDS");
		hm.put("AUDITLOG", "AUDITDS");
		hm.put("CSO", "CSODS");
		hm.put("CS_AUDITLOG_REPORT", "CSAUDITDS");
		hm.put("CRF", "CRFDS");
		hm.put("CIF", "CIFDS");
		hm.put("CRDM", "RDMDS");
		hm.put("CPF", "CPFDS");
		hm.put("CMSTAGING_PRI", "PRIDS");
		hm.put("ARCHIVE", "ARCHIVAL");
		hm.put("CRPT", "CRPT");
		hm.put("JMS_STORE", "JMS");
		hm.put("CIMS", "CIMS");
		hm.put("PL", "PLDS");
		hm.put("CDM", "CDM");
		hm.put("CPC", "CPC");
		context.put("hm", hm);
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
	
		t.merge(context, writer);
		return writer.toString();
	}
	public String getSyncupCompletionMsg123(int syncupId, int workflowId, String baseEnvironment, String childEnvironment, String date, String templateFile,List<String> ScriptNames, String syncType, String release) throws InterruptedException
	{
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("syncupId", syncupId);
		context.put("workflowid", workflowId);
		context.put("baseEnvironment", baseEnvironment);
		context.put("childEnvironment", childEnvironment);
		context.put("date", date);
		  context.put("syncType", syncType);
		  context.put("release", release);
		//context.put("generatedBy", generatedBy);
		//context.put("status", status);
		
		//context.put("binaryList",BinaryNames);
		ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
		Map<String, String> map;

		//BufferedReader file = new BufferedReader(new InputStreamReader(
				//new FileInputStream(timetaken)));

		//String line;

		int totaltime = 56;
		int hours = 0;
		int minutes = 0;
		int seconds = 0;
		
		for(String line : ScriptNames)
		{
			String[] content = line.split("----");

			map = new HashMap<String, String>();

			map.put("scriptName", content[0]);
			map.put("startTime", content[1]);
			map.put("endTime", content[2]);
			map.put("totalTime", content[3]);
			map.put("schemaName", content[4]);
			//totaltime = totaltime + Integer.parseInt(content[3]);
			list.add(map);
			//int totaltime = 65;
			

		}
		//context.put("databaseip", content1[0]);
		//context.put("databasesid", content1[1]);
		//context.put("recordList", list);
		//context.put("totalTimeTaken", totaltime);
		//context.put("recordList", list);
		/*context.put("hours", hours);
		context.put("minutes", minutes);
		context.put("seconds", seconds);*/
			//System.out.println(map);
		
		//Date startTime= new Date();
		//startTime = "Mon Aug 01 14:41:14 IST 2016";
		//System.out.println(new Date());
		//Thread.sleep(5*60*10);
		//Date endTime=new Date();
		//System.out.println(new Date());
		//long diff = endTime.getTime() - startTime.getTime();
		//long diffInMinutes = (int) TimeUnit.MILLISECONDS.toSeconds(diff);
		
		int diffInMinutes = 0;
		  hours = (int) (diffInMinutes / 3600);
	       minutes = (int) ((diffInMinutes % 3600) / 60);
	       seconds = (int) ((diffInMinutes % 3600) % 60);
	      String completionTime = "";
	      
	      if(hours > 0)
	      {
	    	  completionTime = completionTime+hours+" "+" Hours ";
	      }
	      if(minutes > 0)
	      {
	    	  completionTime = completionTime+minutes+" "+" Minutes ";
	      }
	      if(seconds > 0)
	      {
	    	  completionTime = completionTime+seconds+" "+" Seconds ";
	      }
	      if(hours == 0 && minutes == 0 && seconds == 0)
	      {
	    	  completionTime = completionTime+"0 Seconds";
	      }
		//context.put("databaseip", content1[0]);
		//context.put("databasesid", content1[1]);
	      System.out.println(hours);
	      System.out.println(minutes);
	      System.out.println(seconds);
	      System.out.println(completionTime);
		context.put("recordList", list);
		context.put("totalTimeTaken", completionTime);

		

		
		
		//file.close();
		
		
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();
	
}

	
	public String getChangeLogMsg(String templateFile, Map<String, List<Map<String,String>>> changeLog)
	{
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		
		if(!changeLog.isEmpty())
		{
			context.put("changeLogFlag", "1");
			context.put("map", changeLog);
		}
		else
			context.put("changeLogFlag", "0");
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();
	
}
	
	public String getImportMsg(String templateFile, String schemaName, String dumpFileName, String domain, String status) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("schemaname", schemaName);
		context.put("dumpfilename", domain);
		context.put("oldschemaname", status);
		context.put("dumpfilepath", dumpFileName);
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}
	
	public String getSyncupFailureMsg(String templateFile, String env, String msg) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("env", env);
		context.put("msg", msg);
		
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}
	public String getSyncupStartMsg(int syncupId, int workflowId, String baseEnvironment, String childEnvironment, String date, String generatedBy, String status, String release, int pipelineid, String templateFile)
	{
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();
		
		VelocityContext context = new VelocityContext();
		context.put("syncupId", syncupId);
		context.put("workflowId", workflowId);
		context.put("baseEnvironment", baseEnvironment);
		context.put("childEnvironment", childEnvironment);
		context.put("date", date);
		context.put("release", release);
		context.put("generatedBy", generatedBy);
		context.put("status", status);
		context.put("pipelineid", pipelineid);
		
		
			try {

				ArrayList<Map<String, String>> taskList = new ArrayList<Map<String, String>>();
				Map<String, String> taskMap = null;
				
				/*for (String sd : td) {
					System.out.println(sd);
					taskMap = new HashMap<String, String>();
					taskMap.put("tasktype", sd.getTaskType()+"_"+sd.getTaskIdentifier());
					taskMap.put("taskstatus", sd.getStatus());
					if(sd.getTaskParams().equals(null) || sd.getTaskParams().equals("") || sd.getTaskParams().equals(" ") || sd.getTaskParams().equals("null"))
					{
						taskMap.put("taskparameters", "NA");
					}
					else
					{
						taskMap.put("taskparameters", sd.getTaskParams());
					}
					
					taskList.add(taskMap);

				}*/

				//context.put("taskList", taskList);

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			
			
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();
	}
	
	
	
	public String getBuildManifestMsg(String templateFile) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();

		
		/*if(!binaries.isEmpty())
		{
			context.put("deployFlag", "1");
			context.put("binaryList", binaries);
		}
		else
			context.put("deployFlag", "0");
		
		if(!scriptList.isEmpty())
		{
			context.put("scriptflag", "1");
			context.put("recordList", scriptList);
		}
		
		if(!tasks.isEmpty())
		{
			context.put("taskList", tasks);
		}
		if(!changeLog.isEmpty())
		{
			context.put("changeLogFlag", "1");
			context.put("map", changeLog);
		}
		else
			context.put("changeLogFlag", "0");
		*/
		
		
		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}
	
	public String getSyncupCompletionMsg(int syncupId, int workflowId, Map<String,String> binaryStatusMap, String templateFile)
	 {
	  VelocityEngine ve = new VelocityEngine();
	  ve.setProperty("runtime.log", logLocation+"/velocity.log");
	  ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
	  ve.setProperty("classpath.resource.loader.class",
	    ClasspathResourceLoader.class.getName());
	  ve.init();

	  VelocityContext context = new VelocityContext();
	  context.put("syncupId", syncupId);
	  context.put("workflowid", workflowId);
	/*  context.put("baseEnvironment", baseEnvironment);
	  context.put("childEnvironment", childEnvironment);
	  context.put("date", date);
	  context.put("status", status);
	  context.put("pipelineid", pipelineid);
	  context.put("syncType", syncType);
	  context.put("release", release);
	  context.put("generatedBy", generatedBy);
	  context.put("approvedBy", approvedBy);*/
	  //context.put("statusMap", binaryStatusMap);
	  
	  
	  try {

			ArrayList<Map<String, String>> taskList = new ArrayList<Map<String, String>>();
			Map<String, String> taskMap = null;
			
			/*for (SyncTaskDetails sd : td) {
				System.out.println(sd);
				taskMap = new HashMap<String, String>();
				taskMap.put("tasktype", sd.getTaskType()+"_"+sd.getTaskIdentifier());
				taskMap.put("taskstatus", sd.getStatus());
				if(sd.getTaskParams().equals(null) || sd.getTaskParams().equals("") || sd.getTaskParams().equals(" ") || sd.getTaskParams().equals("null"))
				{
					taskMap.put("taskparameters", "NA");
				}
				else
				{
					taskMap.put("taskparameters", sd.getTaskParams());
				}
				
				taskList.add(taskMap);

			}*/

			context.put("taskList", taskList);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  
	  List<String> BinaryNames = new ArrayList<String>();
	  List<String> ScriptNames = new ArrayList<String>();
	  List<String> SchemaNames = new ArrayList<String>();
	  List<String> ExportDetails = new ArrayList<String>();
	 /* for(String key : map123.keySet())
	  { 
	   if(key.equals("BinaryList") && !map123.get(key).isEmpty() ){
	   System.out.println(key);
	   for(String list : map123.get(key))
	   {
	    System.out.println(list);
	    BinaryNames.add(list);
	   }
	   System.out.println(BinaryNames);
	   }
	   
	   if(key.equals("ScriptList")&& !map123.get(key).isEmpty()){
	   for(String list11 : map123.get(key))
	   {
	    System.out.println(list11);
	    ScriptNames.add(list11);
	   }
	   System.out.println(ScriptNames);
	  }
	   if(key.equals("SchemaList")&& !map123.get(key).isEmpty()){ 
	    for(String list12 : map123.get(key))
	    {
	     System.out.println(list12);
	     SchemaNames.add(list12);
	    }
	    System.out.println(SchemaNames);
	   }
	   if(key.equals("exportList")&& !map123.get(key).isEmpty()){ 
	    for(String list13 : map123.get(key))
	    {
	     System.out.println(list13);
	     ExportDetails.add(list13);
	    }
	    System.out.println(ExportDetails);
	   }
	  } */
	  if(!binaryStatusMap.isEmpty())
	  { 
	   context.put("deployflag", "1");
	   context.put("binaryStatusMap",binaryStatusMap);
	  }
	  else
	   context.put("deployflag", "0");
	  
	  
	/*  if(!ScriptNames.isEmpty())
		{
			context.put("scriptflag", "1");
			ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
			Map<String, String> map;
			String[] content1 = null ;
			int totaltime = 0;
			for(String line1 : ScriptNames)
			{
				System.out.println("line1 "+line1);
				content1 = line1.split("-/-");
				String scriptName = content1[2];
				System.out.println("scriptName "+scriptName);
				
				String[] content = scriptName.split("----");
	
				map = new HashMap<String, String>();
	
				map.put("scriptName", content[0]);
				map.put("startTime", content[1]);
				map.put("endTime", content[2]);
				map.put("totalTime", content[3]);
				map.put("schemaName", content[4]);
				totaltime = totaltime + Integer.parseInt(content[3]);
				
				list.add(map);
	
			}*/
			
			 /*int hours = totaltime / 3600;
		      int minutes = (totaltime % 3600) / 60;
		      int seconds = (totaltime % 3600) % 60;
		      String completionTime = "";
		      
		      if(hours > 0)
		      {
		    	  completionTime = completionTime+hours+" "+" Hours ";
		      }
		      if(minutes > 0)
		      {
		    	  completionTime = completionTime+minutes+" "+" Minutes ";
		      }
		      if(seconds > 0)
		      {
		    	  completionTime = completionTime+seconds+" "+" Seconds ";
		      }
		      if(hours == 0 && minutes == 0 && seconds == 0)
		      {
		    	  completionTime = completionTime+"0 Seconds";
		      }
			context.put("databaseip", content1[0]);
			context.put("databasesid", content1[1]);
			context.put("recordList", list);
			//context.put("totalTimeTaken", totaltime);
			context.put("totalTimeTaken", completionTime);
			
		}
		else
			context.put("scriptflag", "0");
			*/
	  
	  if(!SchemaNames.isEmpty())
	  { 
	   context.put("schemaflag", "1");
	   ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
	   Map<String, String> map;
	  
	   String[] dbdetail = null;
	   for(String schemaDetail : SchemaNames)
	   {
	    dbdetail = (schemaDetail.split("-/-"));
	    map = new HashMap<String, String>();
	    map.put("schemaName", dbdetail[0]);
	    map.put("username", dbdetail[1]);
	    map.put("password", dbdetail[2]);
	    map.put("serviceName", dbdetail[3]);
	    map.put("hostName", dbdetail[4]);
	    map.put("port", dbdetail[5]);
	   
	    list.add(map);
	   }
	   String[] exportdetail = null;
	   String exportschema = "";
	   String exportdatabase ="";
	   if(!ExportDetails.isEmpty())
	   {
		   
		   
		   for(String Export : ExportDetails)
		   {
			   exportdetail = (Export.split("-/-"));
			   exportschema = exportdetail[0];
			   exportdatabase = exportdetail[1];
			 
		   }
	   }
	   context.put("schemaList", list);
	   context.put("newschema",dbdetail[6]);
	   context.put("secondserver", dbdetail[7]);
	   context.put("oldschema", exportschema);
	   context.put("firstserver", exportdatabase);
	  }
	  else
	   context.put("schemaflag", "0");
	  
	  
	  
	  Template t = ve.getTemplate(templateFile + ".vm");
	  StringWriter writer = new StringWriter();
	  t.merge(context, writer);
	  return writer.toString();
	 
	}
	
	
	public String getReleaseNotesMsg_debug(String templateFile, String releaseVersion, String baseVersion, String ftpPath, String totalSize, String completionTime) {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty("runtime.log", logLocation+"/velocity.log");
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class",
				ClasspathResourceLoader.class.getName());
		ve.init();

		VelocityContext context = new VelocityContext();
		context.put("releaseVersion", releaseVersion);
		context.put("baseVersion", baseVersion);
		context.put("ftpPath", ftpPath);
		context.put("totalSize", totalSize);
//		context.put("startTime", startTime);
//		context.put("endTime", endTime);
		context.put("totalTime", completionTime);

		Template t = ve.getTemplate(templateFile + ".vm");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		return writer.toString();

	}
	
	
	public String getServerStartWarningMsg(String templatefile) {
			  // TODO Auto-generated method stub

		ArrayList<Map<String, String>> arraylist = new ArrayList<Map<String, String>>();
		Map<String, String> newmap;
		String schemaName="CM_1000_ENT,CM_1001_ENT";
		
		List<String> schemalist =Arrays.asList(schemaName.split(","));
		for (String schema : schemalist) {

			
			newmap = new HashMap<String, String>();
			newmap.put("taskname", schema);
			newmap.put("parameter", "1611");
			newmap.put("taskstatus","PENDING");
			arraylist.add(newmap);
	}
			  VelocityEngine ve = new VelocityEngine();
			  ve.setProperty("runtime.log", logLocation+"/velocity.log");
			  ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
			  ve.setProperty("classpath.resource.loader.class",
			    ClasspathResourceLoader.class.getName());
			  ve.init();

			  VelocityContext context = new VelocityContext();
			  context.put("taskList", arraylist);
			  Template t = ve.getTemplate(templatefile + ".vm");
			  StringWriter writer = new StringWriter();
			  t.merge(context, writer);
			  return writer.toString();
			 }
	
	public static void main(String[] args) throws IOException, InterruptedException {
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("hello", "Passed");
		map.put("welcome", "FAILED");
		map.put("helloworld", "PASSED");
		map.put("alap", "FAILED");
		System.out.println(map);
		
		Map<String, String> map1 = new LinkedHashMap<String, String>();
		map1.put("username", "CM_SM_SIT");
		map1.put("password", "RjisGew52U");
		
		Map<String, String> map3 = new LinkedHashMap<String, String>();
		map3.put("username", "NOTES_SM_SIT");
		map3.put("password", "KshGewx2f");
		
		Map<String, String> map4 = new LinkedHashMap<String, String>();
		map4.put("username", "STAGING_SM_SIT");
		map4.put("password", "RwGewh2x");
		
		Map<String, String> map5 = new LinkedHashMap<String, String>();
		map5.put("username", "STUB_SM_SIT");
		map5.put("password", "VCDERR45TIL");
		
		Map<String, String> map6 = new LinkedHashMap<String, String>();
		map6.put("username", "AUDITLOG_SM_SIT");
		map6.put("password", "sfgDH76sds");
		
		Map<String, String> map7 = new LinkedHashMap<String, String>();
		map7.put("username", "CSO_SM_SIT");
		map7.put("password", "CXWERRT56");
		
		Map<String, String> map8 = new LinkedHashMap<String, String>();
		map8.put("username", "CENTURY_SHIELD_SM_SIT");
		map8.put("password", "FRNCGHKLP");
		
		Map<String, String> map9 = new LinkedHashMap<String, String>();
		map9.put("username", "CS_AUDITLOG_REPORT_SM_SIT");
		map9.put("password", "RTYNDFRTU");
		
		Map<String, String> map10 = new LinkedHashMap<String, String>();
		map10.put("username", "CRF_SM_SIT");
		map10.put("password", "dT2c8pd5sik");
		
		Map<String, String> map11 = new LinkedHashMap<String, String>();
		map11.put("username", "CIF_SM_SIT");
		map11.put("password", "FRNCGHLP");
		
		Map<String, String> map12 = new LinkedHashMap<String, String>();
		map12.put("username", "CRDM_SM_SIT");
		map12.put("password", "e2mc8pdhW2d");
		
		Map<String, String> map13 = new LinkedHashMap<String, String>();
		map13.put("username", "CPF_SM_SIT");
		map13.put("password", "ss312dW");
		
		Map<String, String> map14 = new LinkedHashMap<String, String>();
		map14.put("username", "CMSTAGING_PRI_SM_SIT");
		map14.put("password", "SvIs312msQW");
		
		Map<String, String> map15 = new LinkedHashMap<String, String>();
		map15.put("username", "ARCHIVE_SM_SIT");
		map15.put("password", "ENx22sdC2z");
		
		Map<String, String> map16 = new LinkedHashMap<String, String>();
		map16.put("username", "CRPT_SM_SIT");
		map16.put("password", "d2H2w6gcb");
		
		Map<String, String> map17 = new LinkedHashMap<String, String>();
		map17.put("username", "JMS_STORE_SM_SIT");
		map17.put("password", "ENx22GdC2z");
		
		Map<String, Map<String, String>> map2 = new LinkedHashMap<String, Map<String, String>>();
		map2.put("CM", map1);
		map2.put("NOTES", map3);
		map2.put("STAGING", map4);
		map2.put("STUB", map5);
		map2.put("AUDITLOG", map6);
		map2.put("CSO", map7);
		map2.put("CENTURY_SHIELD", map8);
		map2.put("CS_AUDITLOG_REPORT", map9);
		map2.put("CRF", map10);
		map2.put("CIF", map11);
		map2.put("CRDM", map12);
		map2.put("CPF", map13);
		map2.put("CMSTAGING_PRI", map14);
		map2.put("ARCHIVE", map15);
		map2.put("CRPT", map16);
		map2.put("JMS_STORE", map17);
		
		
		System.out.println(map2.size());
		
		List<String> BinaryNames = new ArrayList<String>();
		BinaryNames.add("cm-webapp-1607.01.014-71126-158.war");
		BinaryNames.add("cm-webapp-1607.01.014-71126-158.war");
		
		List<String> ScriptNames = new ArrayList<String>();
		ScriptNames.add("01_cso_dml_delete_screen_process.sql----2016-03-22_14-05-34----2016-03-22_14-05-35----1----cs_auditlog_report");
		ScriptNames.add("01_cso_dml_delete_screen_process.sql----2016-03-22_14-05-34----2016-03-22_14-05-35----1----cso");
		
		Map<String, List<Map<String, String>>> outermap = new HashMap<String, List<Map<String,String>>>();
		
		List<Map<String, String>> list = new ArrayList<Map<String,String>>();
		
		Map<String, String> innermap1 = new HashMap<String, String>();
		innermap1.put("author", "dhayanand.b");
		innermap1.put("revision", "71572");
		innermap1.put("changes", "/Firefly/trunk/SourceCode/servordermgmt-ui/src/main/java/com/excelacom/century/servordermgmt/helper/NCDActionServOrderMgmtHelper.java --> M");
		innermap1.put("message", "1606 - TSP Code change for tech Change order - CFR-7604");
		
		
		Map<String, String> innermap2 = new HashMap<String, String>();
		innermap2.put("author", "dhayanand.b");
		innermap2.put("revision", "71573");
		innermap2.put("changes", "/Firefly/trunk/SourceCode/servordermgmt-service/src/main/java/com/excelacom/century/servordermgmt/cm/sm/servicemgmt/dao/impl/ordersubmission/OrderSubmissionDAOImpl.java --> M,/Firefly/trunk/SourceCode/servordermgmt-service/src/main/java/com/excelacom/century/servordermgmt/cm/sm/servicemgmt/dao/impl/ordersubmission/helper/OrderSubmissionDAOSupportHelper.java --> M");
		innermap2.put("message", "R 16.07:Prog:Action Type is sent as No Change for TMS Feature when Renewal order is placed over EDI+TMS in Singleview Integration & CFR-7919");
		
		Map<String, String> innermap3 = new HashMap<String, String>();
		innermap3.put("author", "dhayanand.b");
		innermap3.put("revision", "71574");
		innermap3.put("changes", "/Firefly/trunk/SourceCode/pom.xml --> M");
		innermap3.put("message", "R 16.07:Prog:Action Type is sent as No Change for TMS Feature when Renewal order is placed over EDI+TMS in Singleview Integration & CFR-7919");
		
		Map<String, String> innermap4 = new HashMap<String, String>();
		innermap4.put("author", "ddineshkumar.k");
		innermap4.put("revision", "71575");
		innermap4.put("changes", "/Firefly/trunk/SourceCode/ui-e911address/src/main/java/com/excelacom/century/cm/e911address/constants/UIE911AddressConstants.java --> M,/Firefly/trunk/SourceCode/ui-e911address/src/main/java/com/excelacom/century/cm/e911address/controller/UIE911AddressController.java --> M,/Firefly/trunk/SourceCode/century-productmgmt/src/main/java/com/excelacom/century/cm/productmgmt/constants/QueryConstants.java --> M,/Firefly/trunk/SourceCode/servordermgmt-process-bss-api/src/main/java/com/excelacom/century/cm/process/delegator/impl/ProcessDelegatorImpl.java --> M");
		innermap4.put("message", "code committed for US632970 - SIP - VOICE PROVISIONER - GET NPA AUTOMATICALLY (vidya) - CFR-6207");
		
		Map<String, String> innermap7 = new HashMap<String, String>();
		innermap7.put("author", "dineshkumar.k");
		innermap7.put("revision", "25620");
		innermap7.put("changes", "/Firefly/trunk/SourceCode/pom.xml --> M");
		innermap7.put("message", "code committed for pom version increment (Vidya) - CFR-6207");
		
		
		list.add(innermap1);
		list.add(innermap2);
		list.add(innermap3);
		list.add(innermap4);
		list.add(innermap7);
		
		List<Map<String, String>> list1 = new ArrayList<Map<String,String>>();
		
		Map<String, String> innermap5 = new HashMap<String, String>();
		innermap5.put("author", "dineshkumar.k");
		innermap5.put("revision", "25619");
		innermap5.put("changes", "/Firefly/trunk/SourceCode/uitask-webapp/src/main/webapp/scripts/dynamicui/dynamicUIPages.js --> M,/Firefly/trunk/SourceCode/uitask-webapp/src/main/webapp/scripts/lookup/BulkUpdate.js --> M");
		innermap5.put("message", "code committed for US632970 - SIP - VOICE PROVISIONER - GET NPA AUTOMATICALLY (vidya) & CFR-4217");
		
		
		Map<String, String> innermap6 = new HashMap<String, String>();
		innermap6.put("author", "dineshkumar.k");
		innermap6.put("revision", "25620");
		innermap6.put("changes", "/Firefly/trunk/SourceCode/pom.xml --> M");
		innermap6.put("message", "code committed for pom version increment (Vidya) - CFR-6207");
		
		
		
		list1.add(innermap5);
		list1.add(innermap6);
		//list1.add(innermap6);
		
		
		/*List<Map<String, String>> list2 = new ArrayList<Map<String,String>>();
		
		Map<String, String> innermap7 = new HashMap<String, String>();
		innermap7.put("author", "cba");
		innermap7.put("revision", "321");
		innermap7.put("changes", "shhsjdhjshjkdshhsjdhjshjkdshhsjdhjshjkdshhsjdhjshjkdshhsjdhjshjkdkrishhsjdhjshjkdhjsjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddjjjjjjjjjjjjjjjj");
		
		
		Map<String, String> innermap8 = new HashMap<String, String>();
		innermap8.put("author", "gfe");
		innermap8.put("revision", "654");
		innermap8.put("changes", "prish");
		
		
		Map<String, String> innermap9 = new HashMap<String, String>();
		innermap9.put("author", "ihj");
		innermap9.put("revision", "987");
		innermap9.put("changes", "mdsds");
		
		list2.add(innermap7);
		list2.add(innermap8);
		list2.add(innermap9);*/
		
		
		outermap.put("CM", list);
		outermap.put("UITASK", list1);
		//outermap.put("CSO", list2);
		
		System.out.println(outermap);
		
		
		List<String> l11 = new ArrayList<String>();
		l11.add("cmserviceapp");
		l11.add("csoservice");
		
		List<String> t11 =new ArrayList<String>();
		t11.add("dsdsds");
		t11.add("dsds");
		Map<String,Map<String,String>> binaryStatusMap1 = new HashMap<String, Map<String,String>>();
		
		Map<String,String> binaryStatusMap = new HashMap<String, String>();
		binaryStatusMap.put("cm-webapp-1605.01.001-67864-223.war", "RUNNING");
		binaryStatusMap.put("PricePlanCache", "RUNNING");
		binaryStatusMap.put("hotdeploy", "RUNNING");
		binaryStatusMap.put("ServiceMix-1604.02.006-2928-10.war", "RUNNING");
		binaryStatusMap.put("ServiceMix-1604.02.006-2928-10.war123", "RUNNING");
		binaryStatusMap.put("ServiceMix-1604.02.006-2928-10.war456", "FAILED");
		binaryStatusMap1.put("CM",binaryStatusMap);
		binaryStatusMap1.put("CIF",binaryStatusMap);
		binaryStatusMap1.put("CIF",binaryStatusMap);
		binaryStatusMap1.put("CSO",binaryStatusMap);
		binaryStatusMap1.put("CSO",binaryStatusMap);
		

		
		
		EmailNotification en = new EmailNotification("mail.excelacom.in", "2525", "shanmugapriya.m@excelacom.in", "Artifact Upload", "Artifact-Uploader@excelacom.in", "E:\\Joystick\\eclipse\\Workspace\\EmailTemplateValidator");
		//String mesg=en.dbtemplate("SyncUpCompletion", "MIRTOON", "RD-RAC1-SCAN.excelacom.in", "1521", map2);
		//String mesg=en.getInvalidJiraIdMsg("The specified JIRA ID is invalid");
		//String mesg=en.getChangeLogMsg("importsuccess", outermap);
		//String mesg=en.getSyncupFailureMsg("syncupFailed", "Hydra", "No log");
		//String mesg=en.getSyncupCompletionMsg(121, 3232, binaryStatusMap, "SyncUpCompletion");
		//String mesg=en.getReleaseNotesMsg_debug("releaseNotes_debug", "1606.22" , "1606.21", "ftp.excelacom.com//Test/suriya/1602_Release/1606.22", "961M", "26 Minutes ");
		//System.out.println("mesg"+mesg);
		//String mesg = en.getBuildManifestMsg("buildflowCompletion");
		//String mesg=en.getSyncupStartMsg(1055, 1524, "hydra", "dwan", "21/7/2016", "kris", "pending", "2454", 457, "buildflowCompletion");
		//String msg=en.getSyncupCompletionMsg(syncupId, workflowId, baseEnvironment, childEnvironment, date, templateFile, ScriptNames)
		//String mesg=en.getBuildManifestMsg("buildflowCompletion", t11, l11, ScriptNames, outermap);
		String mesg=en.getServerStartWarningMsg("importinprogress");
		en.sendEmail(mesg, "shanmugapriya.m@excelacom.in", "Artifact Upload - Successfull [ 16.01 ModuleUploaded : JP Version : 1.101 Project : CCSIP");
		//en.getChangeLogMsg("buildflowCompletion", outermap);
		
	}
	}

