/*
 Copyright 2014 Trustees of Indiana University

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

import java.net.InetSocketAddress;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;

public interface Slicer {

	List <OFFlowMod> allowedFlows(OFFlowMod flow);
	List <OFFlowMod> managedFlows(OFFlowMod flow);
	List<OFMessage> allowedPacketOut(OFPacketOut output);
	List<OFMessage> managedPacketOut(OFPacketOut output);
	void setController(InetSocketAddress addr);
	InetSocketAddress getControllerAddress();
	void setSwitch(IOFSwitch mySwitch);
	void setMaxFlows(int numberOfFlows);
	void setFlowRate(int flowRate);
	int getMaxFlowRate();
	boolean isGreaterThanMaxFlows(int numberOfFlows);
	boolean isPortPartOfSlice(String portName);
	boolean isPortPartOfSlice(short portId);
	void setPortConfig(String portName, PortConfig pConfig);
	PortConfig getPortConfig(String portName);
	PortConfig getPortConfig(short portId);
	boolean isOkToProcessMessage();
	String hasOverlap(Slicer otherSlicer);
	String getSliceName();
	void setSliceName(String name);
	double getRate();
	int getMaxFlows();
	int getPacketInRate();
	void setPacketInRate(int rate);
	IOFSwitch getSwitch();
	void addBufferId(int bufferId, byte[] packetData);
	void setPortId(String portName, short portId);
	void setAdminState(boolean state);
	boolean getAdminState();
	void setTagManagement(boolean tagMgmt);
	void setFlushRulesOnConnect(boolean fluhsRulesOnConnect);
	boolean getFlushRulesOnConnect();
	boolean getTagManagement();
	boolean doTimeouts();
	void setDoTimeouts(boolean doTimeouts);
	void setSwitchName(String swName);
	String getSwitchName();
}
