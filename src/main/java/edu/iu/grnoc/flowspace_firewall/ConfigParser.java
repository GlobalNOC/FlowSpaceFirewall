/*
 Copyright 2013 Trustees of Indiana University

   Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.iu.grnoc.flowspace_firewall;

import java.io.File;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Slices FlowStats based on the Slicer and the stats passed.
 * @author aragusa
 *
 */
public final class ConfigParser {
	 
	//the logger
	private static final Logger log = LoggerFactory.getLogger(ConfigParser.class);

	private static boolean isValidConfig(List<HashMap<Long, Slicer>> slices){
		
		if(slices.size() == 0){
			return false;
		}
		
		for(HashMap<Long, Slicer> slice : slices){
			for(Long dpid : slice.keySet()){
				Slicer config = slice.get(dpid);
				for(HashMap<Long, Slicer> otherSlice : slices){
					if(otherSlice.containsKey(dpid)){
						Slicer otherConfig = otherSlice.get(dpid);
						if(otherConfig.getSliceName() != config.getSliceName()){
							if(config.hasOverlap(otherConfig)){
								//TODO: Log config has overlap here!
								log.warn("Overlap detected between slice "+config.getSliceName()+" and slice "
								+otherConfig.getSliceName()+" will not load this configuration");
								return false;
							}
						}
					}
				}
			}
		}
		
		return true;
	}
	
	//the only method we need here
	public static ArrayList<HashMap<Long, Slicer>> parseConfig(String xmlFile) throws IOException, SAXException, ParserConfigurationException,XPathExpressionException{
		ArrayList<HashMap<Long, Slicer>> newSlices = new ArrayList<HashMap<Long, Slicer>>();
		Document document;
    	try {
    		DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    		
    		document = parser.parse(new File(xmlFile));
    	
	        // create a SchemaFactory capable of understanding WXS schemas
	        SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
	
	        // load a WXS schema, represented by a Schema instance
	        Source schemaFile = new StreamSource(new File("/etc/fsfw/fsfw.xsd"));
	        Schema schema = factory.newSchema(schemaFile);
	        
	        // create a Validator instance, which can be used to validate an instance document
			Validator validator = schema.newValidator();
	       
	        // validate the DOM tree
	        validator.validate(new DOMSource(document));
    	
	        //get a list of all switches and slices
	        XPath xPath = XPathFactory.newInstance().newXPath();
	        XPathExpression switchExpression = xPath.compile("/flowspace_firewall/switch");
	        XPathExpression sliceExpression = xPath.compile("/flowspace_firewall/slice");
	        NodeList switches = (NodeList) switchExpression.evaluate(document,XPathConstants.NODESET);
	        NodeList slices = (NodeList) sliceExpression.evaluate(document,XPathConstants.NODESET);
	        
	        HashMap<String,Long> switchDPID = new HashMap<String,Long>();
	        
	        for(int i=0;i<switches.getLength();i++){
	        	Node mySwitch = switches.item(i);
	        	String dpidStr = (String) mySwitch.getAttributes().getNamedItem("dpid").getTextContent();
	        	Long DPID = HexString.toLong(dpidStr);
	        	switchDPID.put(mySwitch.getAttributes().getNamedItem("name").getTextContent(), DPID);
	        }
	              
	        
	        //loop through all the slices
	        for(int i=0;i<slices.getLength();i++){
	        	Node slice = slices.item(i);
	        	
	        	NodeList sliceSwitches = slice.getChildNodes();
	        	//have to store a dpid to slicer mapping for every switch configured
	        	HashMap<Long, Slicer> dpidSlicer = new HashMap<Long,Slicer>();
	        	log.debug("Processing Config for Slice: " + slice.getAttributes().getNamedItem("name").getTextContent());
	        	String sliceName = slice.getAttributes().getNamedItem("name").getTextContent();
	        	InetSocketAddress controllerAddr = new InetSocketAddress("0.0.0.0",6633);
	        	//loop through all the switches for the slice
	        	for(int j=0;j<sliceSwitches.getLength();j++){
	        		Node switchConfig = sliceSwitches.item(j);
	        		//need to init the socket addr... we will change later...
	        		
	        		//is our current thing a switch or a controller?
	        		log.debug("NodeName: '" + switchConfig.getNodeName() +"'");
	        		if(switchConfig.getNodeName().equals("switch")){
	        			//first verify this switch is in our config... if not stop
	        			log.debug("processing switch");
	        			if(switchDPID.containsKey((switchConfig.getAttributes().getNamedItem("name").getTextContent()))){
	        				//ok create a slicer
	        				log.debug("Processing Slice for Switch: " + switchConfig.getAttributes().getNamedItem("name"));
	        				Slicer slicer = new VLANSlicer();
	        				slicer.setSliceName(sliceName);
	        				boolean flush_on_connect = Boolean.parseBoolean(switchConfig.getAttributes().getNamedItem("flush_rules_on_connect").getTextContent());
	        				Node tag_mgmt = switchConfig.getAttributes().getNamedItem("tag_management");
	        				boolean tag_management = false;
	        				if(tag_mgmt != null){
	        					tag_management = Boolean.parseBoolean(switchConfig.getAttributes().getNamedItem("tag_management").getTextContent());
	        				}
	        				slicer.setTagManagement(tag_management);
	        				slicer.setFlushRulesOnConnect(flush_on_connect);
	        				int numberOfFlows = Integer.parseInt(switchConfig.getAttributes().getNamedItem("max_flows").getTextContent());
	        				slicer.setMaxFlows(numberOfFlows);      				
	        				int flowRate = Integer.parseInt(switchConfig.getAttributes().getNamedItem("flow_rate").getTextContent());
	        				slicer.setFlowRate(flowRate);
	        				int packetInRate = Integer.parseInt(switchConfig.getAttributes().getNamedItem("packet_in_rate").getTextContent());
	        				slicer.setPacketInRate(packetInRate);
	        				Node do_timeouts_node = switchConfig.getAttributes().getNamedItem("do_timeouts");
	        				boolean do_timeouts = false;
	        				if(do_timeouts_node != null){
	        					do_timeouts = Boolean.parseBoolean(switchConfig.getAttributes().getNamedItem("do_timeouts").getTextContent());
	        				}
	        				slicer.setDoTimeouts(do_timeouts);
	        				NodeList ports = switchConfig.getChildNodes();
	        				//for every port create a port config
	        				for(int k=0; k < ports.getLength(); k++){
		        				Node port = ports.item(k);
		        				if(!port.getNodeName().equals("port")){
		        					continue;
		        				}
		        				PortConfig pConfig = new PortConfig();
		        				log.debug(port.toString());
		        				pConfig.setPortName(port.getAttributes().getNamedItem("name").getTextContent());
		        				log.debug("processing interface: " + pConfig.getPortName());
		        				NodeList ranges = port.getChildNodes();
		        				VLANRange myRange = new VLANRange();
		        				//for every range element add to our vlan range
		        				if(tag_management == true && ranges.getLength() > 1){
		        					log.error("Tag Management can only be used on a single VLAN, please fix config and try again");
		        					newSlices.clear();
		        					return newSlices;
		        				}
		        				
		        				for(int l=0; l < ranges.getLength(); l++){
		        					Node range = ranges.item(l);
		        					if(!range.getNodeName().equals("range")){
		        						continue;
		        					}
		        					if(tag_management && Short.parseShort(range.getAttributes().getNamedItem("start").getTextContent()) != Short.parseShort(range.getAttributes().getNamedItem("end").getTextContent())){
		        						log.error("Tag Mangement can only be used on a single VLAN, please fix config and try again");
		        						newSlices.clear();
		        						return newSlices;
		        					}
		        					for(short m = Short.parseShort(range.getAttributes().getNamedItem("start").getTextContent()); 
		        							m <= Short.parseShort(range.getAttributes().getNamedItem("end").getTextContent()); m++){
		        						myRange.setVlanAvail(m, true);
		        					}
		        				}
		        				//add the vlanRange to the portConfig
		        				pConfig.setVLANRange(myRange);
		        				//add the port config to the slicer
		        				slicer.setPortConfig(pConfig.getPortName(), pConfig);
		        			}
	        				//add the slicer to the whole slice container (all switches)
		        			dpidSlicer.put(switchDPID.get(switchConfig.getAttributes().getNamedItem("name").getTextContent()), slicer);
	        			}else{
	        				//unable to find the switch named (switchConfig.getAttributes().getNamedItem("name").toString()) in our config	        			
	        			}
	        		}else if(switchConfig.getNodeName().equals("controller")){
	        			//must be the controller
	        			controllerAddr = new InetSocketAddress(switchConfig.getAttributes().getNamedItem("ip_address").getTextContent(),
	        					Integer.parseInt(switchConfig.getAttributes().getNamedItem("port").getTextContent()));
	        		}
	        		
	        	}
	        	//done parsing all the XML here...
        		//need to set all slices to the correct controller addr
        		for(Long key : dpidSlicer.keySet()){
        			//set the controller
        			log.debug("setting slice controller to: " + controllerAddr.getHostName() + ":" + controllerAddr.getPort());
        			dpidSlicer.get(key).setController(controllerAddr);
        		}
	        	//add to the new configuration slicer object
	        	newSlices.add(dpidSlicer);
	        }
    	}catch (SAXException e) {
    		log.error("Problems parsing " + xmlFile + ": " + e.getMessage());
    		throw e;
        }catch (ParserConfigurationException e){
        	log.error("Problems parsing " + xmlFile + ": " + e.getMessage());
        	throw e;
        }catch (XPathExpressionException e){
        	log.error("Problems parsing " + xmlFile + ": " + e.getMessage());
        	throw e;
        }
    	
    	//now validate config
    	if(isValidConfig(newSlices)){
    		return newSlices;
    	}else{
    		log.error("Problem with configuration.  Unable to load config");
    		newSlices.clear();
    		return newSlices;
    	}
	}
	
}
