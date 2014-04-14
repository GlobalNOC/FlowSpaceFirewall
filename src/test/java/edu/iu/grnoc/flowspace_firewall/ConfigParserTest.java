package edu.iu.grnoc.flowspace_firewall;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import static org.junit.Assert.*;

public class ConfigParserTest {
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	@Before
	public void setup(){
		//nothing to do here
	}
	
	/**
	 * tests testInvalidXML
	 * @throws SAXException 
	 * @throws IOException 
	 * @throws ParserConfigurationException 
	 * @throws XPathExpressionException 
	 */
	@Test
	public void testInvalidXML() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException {
		thrown.expect(SAXException.class);
		ConfigParser.parseConfig("src/test/resources/invalid.xml");
	}
	
	/**
	 * tests no file found
	 * @throws ParserConfigurationException 
	 * @throws XPathExpressionException 
	 */
	@Test
	public void testNoFile() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException {
		thrown.expect(FileNotFoundException.class);
		thrown.expectMessage("/no/file/here.xml (No such file or directory)");
		ConfigParser.parseConfig("/no/file/here.xml");
	}
	
	/**
	 * tests validXML, but does not conform to XSD
	 * @throws ParserConfigurationException 
	 * @throws XPathExpressionException 
	 */
	@Test
	public void testNoValidation() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException {
		thrown.expect(SAXException.class);
		thrown.expectMessage("cvc-complex-type.2.4.a: Invalid content was found starting with element 'switchfoo'. One of '{switch}' is expected");
		ConfigParser.parseConfig("src/test/resources/validxml_not_valid_schema.xml");
	}
	
	@Test
	public void testOverlappingFlowSpace() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException{
		ArrayList<HashMap<Long, Slicer>> slices = ConfigParser.parseConfig("src/test/resources/overlapping_flowspace.xml");
		assertTrue("config has overlapping flowspace, slice should be empty was " + slices.size(), slices.size() == 0);
	}
	
	@Test
	public void testNonOverlappingFlowSpaceValidConfig() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException{
		ArrayList<HashMap<Long, Slicer>> slices = ConfigParser.parseConfig("src/test/resources/good_config.xml");
		assertTrue("config is valid!", slices.size() == 2);
	}
	
	@Test
	public void testConfigLoadedProperly() throws IOException, SAXException, XPathExpressionException, ParserConfigurationException{
		ArrayList<HashMap<Long, Slicer>> slices = ConfigParser.parseConfig("src/test/resources/good_config.xml");
		assertTrue("Number of Slices is correct at " + slices.size(), slices.size() == 2);
		HashMap<Long,Slicer> slice = slices.get(0);
		assertNotNull("Slice is not null", slice);
		Long dpid = new Long(3);
		Slicer slicer = slice.get(dpid);
		assertTrue("Slice name expected is 'Slice1' got " + slicer.getSliceName(), slicer.getSliceName().equals("Slice1"));
		assertNotNull("Slicer is not null",slicer);
		
		PortConfig pConfig = slicer.getPortConfig("s2-eth3");
		assertNotNull("PortConfig is not null", pConfig);
		assertTrue("VLAN 2000 is allowed for s2-eth3",pConfig.vlanAllowed((short)2000));
		assertTrue("VLAN 500 is allowed for s2-eth3", pConfig.vlanAllowed((short)500));
		assertTrue("VLAN 1 is allowed for s2-eth3", pConfig.vlanAllowed((short)1));
		assertFalse("VLAN 501 is not allowed for s2-eth3",pConfig.vlanAllowed((short)501));
		assertFalse("VLAN 999 is not allowed for s2-eth3", pConfig.vlanAllowed((short)999));
		assertTrue("VLAN 1000 is allowed for s2-eth3", pConfig.vlanAllowed((short)1000));
		assertTrue("VLAN 499 is allowed for s2-eth3", pConfig.vlanAllowed((short)499));
		
		HashMap<Long,Slicer> otherSlice = slices.get(1);
		assertNotNull("Slice is not null", otherSlice);
		Slicer otherSlicer = otherSlice.get(dpid);
		assertTrue("Slice name expected is 'Slice2' got " + otherSlicer.getSliceName(), otherSlicer.getSliceName().equals("Slice2"));
		PortConfig otherPConfig = otherSlicer.getPortConfig("s2-eth1");
		assertNotNull("PortConfig is not null", otherPConfig);
		assertTrue("Untagged is allowed", otherPConfig.vlanAllowed(VLANRange.UNTAGGED));
		assertFalse("VLAN 1 is not allowed", otherPConfig.vlanAllowed((short)1));
		assertFalse("VLAN 2000 is not allowed", otherPConfig.vlanAllowed((short)2000));
		assertTrue("VLAN 2001 is allowed", otherPConfig.vlanAllowed((short)2001));
		assertTrue("VLAN 4000 is allowed", otherPConfig.vlanAllowed((short)4000));
		assertFalse("VLAN 4001 is not allowed", otherPConfig.vlanAllowed((short)4001));
	}
}
