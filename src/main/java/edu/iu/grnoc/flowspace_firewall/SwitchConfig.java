package edu.iu.grnoc.flowspace_firewall;

public class SwitchConfig {

	private boolean flush_on_connect;
	private Long DPID;
	private String name;
	@SuppressWarnings("unused")
	private boolean install_default_drop;
	
	public SwitchConfig(){
		this.flush_on_connect = false;
		this.install_default_drop = false;
		this.name = "";
		this.DPID = 0L;
	}
	
	
	public String getName(){
		return this.name;
	}
	
	public Long getDPID(){
		return this.DPID;
	}
	
	public boolean getFlushRulesOnConnect(){
		return this.flush_on_connect;
	}
	
	public boolean getInstallDefaultDrop(){
		return this.flush_on_connect;
	}
	
	public void setName(String value){
		this.name = value;
	}
	
	public void setDPID(Long dpid){
		this.DPID = dpid;
	}
	
	public void setFlushRulesOnConnect(boolean value){
		this.flush_on_connect = value;
	}
	
	public void setInstallDefaultDrop(boolean value){
		this.install_default_drop = value;
	}
}
