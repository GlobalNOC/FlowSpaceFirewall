package edu.iu.grnoc.flowspace_firewall;

public class InvalidConfigException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4109117275702846466L;
	private String msg;
	public InvalidConfigException(String msg){
		this.msg = msg;
	}
	
	public String getMsg(){
		return this.msg;
	}
	
}
