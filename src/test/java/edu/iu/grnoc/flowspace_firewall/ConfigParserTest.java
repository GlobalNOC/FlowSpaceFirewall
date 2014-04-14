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
		assertTrue("config is valid!", slices.size() > 1);
	}
	
}
