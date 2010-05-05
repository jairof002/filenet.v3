/**
 * 
 */
package com.google.enterprise.filenet;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.enterprise.common.modules.FileUtils;
import com.google.enterprise.common.modules.Tasks;
import com.thoughtworks.selenium.SeleneseTestCase;

/**
 *  This is a Test Script which will configure a FileNET connector and
 * check that it is configured properly and corresponding files are
 * generated/modified in a proper way.
 * 
 * @author vishwas_londhe
 */
public class FileNet extends SeleneseTestCase
{
	@BeforeClass
	public void setup()
	{
		Tasks.getSession();
		Tasks.LoginToGSA();
		Tasks.gotoConnectorAdministration();
		Tasks.gotoConnectors();
		
		if(Tasks.IsConnectorPresent(FilenetTasks.obj.connectorexists())== true)
			FilenetTasks.DeleteConnectorInstance();
		
		Tasks.selectConnectorManager(Tasks.property.connectormanager());
		Tasks.setConnectorName(FilenetTasks.property.connectorname());
	}
	
	@AfterClass
	public void teardown()
	{
		if(Tasks.selenium.isElementPresent(Tasks.obj.gsalogoutbtn()))
			Tasks.LogoutGSA();
		Tasks.endSession();
	}
	
	@Test
	public void testblankConnectorName()
	{
		Tasks.waitfor(FilenetTasks.obj.txtconnectorpassword());
		Tasks.selenium.type(Tasks.obj.txtconnectorname(), "");
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		
		Tasks.waitfor(Tasks.obj.gsaerrormessage());
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.blankconnectornameerror()));
		Tasks.selenium.type(Tasks.obj.txtconnectorname(),FilenetTasks.property.connectorname());
		
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		
	}
	
	@Test(dependsOnMethods="testblankConnectorName")
	public void testblankUserName()
	{
		Tasks.waitfor(FilenetTasks.obj.txtconnectorpassword());
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.waitfor(Tasks.obj.gsaerrormessage());
		
//		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.blankusernameerror()));
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "blankusername")));
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorusername(),FilenetTasks.property.connectorusername());
	}
	
	@Test(dependsOnMethods="testblankUserName")
	public void testblankPassword()
	{
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(5);
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "blankpassword")));
		
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorpassword(),FilenetTasks.property.connectorpassword());
	}
	
	@Test(dependsOnMethods="testblankPassword")
	public void testblankObjectStore()
	{
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(5);
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "blankobjectstore")));
		
		Tasks.selenium.type(FilenetTasks.obj.txtobjectstore(),FilenetTasks.property.objectstore());
	}
	
	@Test(dependsOnMethods="testblankObjectStore")
	public void testblankContentEngineURL()
	{
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(5);
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "blankcontentengineurl")));
		
		Tasks.selenium.type(FilenetTasks.obj.txtcontentengineurl(),FilenetTasks.property.contentenginurl());
	}
	
	@Test(dependsOnMethods="testblankContentEngineURL")
	public void testblankWorkPlaceURL()
	{
//		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
//		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.blankworkplaceurlerror()));
		Tasks.selenium.type(FilenetTasks.obj.txtworkplaceurl(),FilenetTasks.property.workplaceurl());

//		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
	}
	
	@Test(dependsOnMethods="testblankWorkPlaceURL")
	public void testinvalidUserName()
	{
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorusername(), FilenetTasks.property.connectorusername()+123);
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(15);
		
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "invalidcredentials")));
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorusername(), FilenetTasks.property.connectorusername());
	}
	
	@Test(dependsOnMethods="testinvalidUserName")
	public void testinvalidPassword()
	{
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorpassword(), FilenetTasks.property.connectorpassword()+123);
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(15);
		
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "invalidcredentials")));
		Tasks.selenium.type(FilenetTasks.obj.txtconnectorpassword(), FilenetTasks.property.connectorpassword());
	}
	
	@Test(dependsOnMethods="testinvalidPassword")
	public void testinvalidObjectStore()
	{
		Tasks.selenium.type(FilenetTasks.obj.txtobjectstore(),FilenetTasks.property.objectstore()+123);
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(15);
		
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "invalidobjectstore")));
		Tasks.selenium.type(FilenetTasks.obj.txtobjectstore(),FilenetTasks.property.objectstore());
	}
	
	@Test(dependsOnMethods="testinvalidObjectStore")
	public void testinvalidContentEngineURL()
	{
		Tasks.selenium.type(FilenetTasks.obj.txtcontentengineurl(),FilenetTasks.property.contentenginurl()+123);
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
		Tasks.sleep(15);
		
		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "invalidcontentengineurl")));
		Tasks.selenium.type(FilenetTasks.obj.txtcontentengineurl(),FilenetTasks.property.contentenginurl());
	}
	
	@Test(dependsOnMethods="testinvalidContentEngineURL")
	public void testinvalidWorkPlaceURL()
	{
//		Tasks.selenium.type(Tasks.obj.txtworkplaceurl(),Tasks.property.workplaceurl()+123);
//		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
//		assertEquals(true, Tasks.GetGSAErrorMsg().contentEquals(Tasks.property.geterrormessage("common\\FileNetErrors.properties", "blankpassword")));
		
		Tasks.selenium.type(FilenetTasks.obj.txtworkplaceurl(),FilenetTasks.property.workplaceurl());
		Tasks.setTraversalRate("52");
		Tasks.setRetryDelay("15");
		Tasks.selenium.click(Tasks.obj.saveconfigurationbtn());
	}
	
	@Test(dependsOnMethods="testinvalidWorkPlaceURL")
	public void testIsConnectorRunning()
	{
		Tasks.waitfor(FilenetTasks.obj.connectorexists());
		assertEquals(Tasks.IsConnectorPresent(FilenetTasks.obj.connectorexists()), true);
	}
	
	@Test(dependsOnMethods="testIsConnectorRunning")
	public void testConfigurationSaved()
	{
		assertEquals(FileUtils.CheckConfigSaved(FilenetTasks.property.connectorname(),"baseline\\fpautomation.properties"), true);
	}
	
	@Test(dependsOnMethods="testConfigurationSaved")
	public void testFeedfile()
	{
		assertEquals(FileUtils.CheckFeedFile("baseline\\fpautomationFeedFile.log"), true);
	}
	
	@Test(dependsOnMethods="testFeedfile")
	public void testGSAFeed()
	{
		Tasks.sleep(180);
		Tasks.gotoCurrentFeeds();
		
	}
	
	@Test(dependsOnMethods="testGSAFeed")
	public void testDeleteConnectorInstance()
	{
		FilenetTasks.DeleteConnectorInstance();
		assertEquals(Tasks.IsConnectorPresent(FilenetTasks.property.connectorname()), false);
	}
}