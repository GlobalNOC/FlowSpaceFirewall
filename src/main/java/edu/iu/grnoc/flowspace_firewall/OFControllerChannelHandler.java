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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.HandshakeTimeoutException;
import net.floodlightcontroller.core.internal.SwitchStateException;
import net.floodlightcontroller.storage.StorageException;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.openflow.protocol.*;
import org.openflow.protocol.OFError.*;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Channel handler deals with the switch connection and dispatches
 * switch messages to the appropriate locations.
 * @author readams
 */
class OFControllerChannelHandler
    extends IdleStateAwareChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(OFControllerChannelHandler.class);

    private Channel channel;
    private IOFSwitch sw;
    private Proxy proxy;
    // State needs to be volatile because the HandshakeTimeoutHandler
    // needs to check if the handshake is complete
    private volatile ChannelState state;

    /** transaction Ids to use during handshake. Since only one thread
     * calls into the OFChannelHandler we don't need atomic.
     * We will count down
     */
    private int handshakeTransactionIds = -1;

    /**
     * The state machine for handling the switch/channel state.
     * @author gregor
     */
    enum ChannelState {
        /**
         * Initial state before channel is connected.
         */
        INIT(false) {
            @Override
            void
            processOFMessage(OFControllerChannelHandler h, OFMessage m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFError(OFControllerChannelHandler h, OFError m)
                    throws IOException {
                // need to implement since its abstract but it will never
                // be called
            }

        },

        /**
         * We send a HELLO to the switch and wait for a reply.
         * Once we receive the reply we send an OFFeaturesRequest and
         * a request to clear all FlowMods.
         * Next state is WAIT_FEATURES_REPLY
         */
        WAIT_HELLO(false) {
            @Override
            void processOFHello(OFControllerChannelHandler h, OFHello m)
                    throws IOException {
                //done
                h.setState(READY);
            }

            @Override
            void processOFError(OFControllerChannelHandler h, OFError m) {
                logErrorDisconnect(h, m);
            }

        },



        /**
         * The switch is in MASTER role. We enter this state after a role
         * reply from the switch is received (or the controller is MASTER
         * and the switch doesn't support roles). The handshake is complete at
         * this point. We only leave this state if the switch disconnects or
         * if we send a role request for SLAVE /and/ receive the role reply for
         * SLAVE.
         */
        READY(true) {
            @LogMessageDoc(level="WARN",
                message="Received permission error from switch {} while" +
                         "being master. Reasserting master role.",
                explanation="The switch has denied an operation likely " +
                         "indicating inconsistent controller roles",
                recommendation="This situation can occurs transiently during role" +
                 " changes. If, however, the condition persists or happens" +
                 " frequently this indicates a role inconsistency. " +
                 LogMessageDoc.CHECK_CONTROLLER )
            @Override
            void processOFError(OFControllerChannelHandler h, OFError m)
                    throws IOException {
                // role changer will ignore the error if it isn't for it
               
            }

            @Override
            void processOFStatisticsReply(OFControllerChannelHandler h,
                                          OFStatisticsReply m) {
                
            }
 
        };

        private final boolean handshakeComplete;
        ChannelState(boolean handshakeComplete) {
            this.handshakeComplete = handshakeComplete;
        }

        /**
         * Is this a state in which the handshake has completed?
         * @return true if the handshake is complete
         */
        public boolean isHandshakeComplete() {
            return handshakeComplete;
        }


        /**
         * We have an OFMessage we didn't expect given the current state and
         * we want to treat this as an error.
         * We currently throw an exception that will terminate the connection
         * However, we could be more forgiving
         * @param h the channel handler that received the message
         * @param m the message
         * @throws SwitchStateExeption we always through the execption
         */
        // needs to be protected because enum members are acutally subclasses
        protected void illegalMessageReceived(OFControllerChannelHandler h, OFMessage m) {
            
        }

        /**
         * We have an OFMessage we didn't expect given the current state and
         * we want to ignore the message
         * @param h the channel handler the received the message
         * @param m the message
         */
        protected void unhandledMessageReceived(OFControllerChannelHandler h,
                                                OFMessage m) {
            String msg = "";
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
        }

        /**
         * Log an OpenFlow error message from a switch
         * @param sw The switch that sent the error
         * @param error The error message
         */
        @LogMessageDoc(level="ERROR",
                message="Error {error type} {error code} from {switch} " +
                        "in state {state}",
                explanation="The switch responded with an unexpected error" +
                        "to an OpenFlow message from the controller",
                recommendation="This could indicate improper network operation. " +
                        "If the problem persists restarting the switch and " +
                        "controller may help."
                )
        protected void logError(OFControllerChannelHandler h, OFError error) {
            log.error("{} from controller",
                      new Object[] {
                          getErrorString(error),
                          this.toString()});
        }

        /**
         * Log an OpenFlow error message from a switch and disconnect the
         * channel
         * @param sw The switch that sent the error
         * @param error The error message
         */
        protected void logErrorDisconnect(OFControllerChannelHandler h, OFError error) {
            logError(h, error);
            h.channel.disconnect();
        }

        /**
         * Process an OF message received on the channel and
         * update state accordingly.
         *
         * The main "event" of the state machine. Process the received message,
         * send follow up message if required and update state if required.
         *
         * Switches on the message type and calls more specific event handlers
         * for each individual OF message type. If we receive a message that
         * is supposed to be sent from a controller to a switch we throw
         * a SwitchStateExeption.
         *
         * The more specific handlers can also throw SwitchStateExceptions
         *
         * @param h The OFChannelHandler that received the message
         * @param m The message we received.
         * @throws SwitchStateException
         * @throws IOException
         */
        void processOFMessage(OFControllerChannelHandler h, OFMessage m) throws IOException {
            log.debug("Received message of type: " + m.getType());
            switch(m.getType()) {
                case HELLO:
                    processOFHello(h, (OFHello)m);
                    break;
                case BARRIER_REPLY:
                case ECHO_REPLY:
                	break;
                case ECHO_REQUEST:
                    processOFEchoRequest(h, (OFEchoRequest)m);
                    break;
                case ERROR:
                    processOFError(h, (OFError)m);
                    break;
                case FEATURES_REPLY:
                case FLOW_REMOVED:
                case GET_CONFIG_REPLY:
                case PACKET_IN:
                case PORT_STATUS:
                case QUEUE_GET_CONFIG_REPLY:
                case STATS_REPLY:
                	break;
                case VENDOR:
                	processOFVendor(h, (OFMessage)m);
                	break;
                case SET_CONFIG:
                	processSetConfig(h, (OFMessage)m);
                	break;
                case GET_CONFIG_REQUEST:
                	processOFConfigRequest(h, (OFMessage)m);
                	break;
                case PACKET_OUT:
                	processOFPacketOut(h, (OFMessage)m);
                	break;
                case PORT_MOD:
                	break;
                case QUEUE_GET_CONFIG_REQUEST:
                	processOFQueueGetRequest(h, (OFMessage)m);
                	break;
                case BARRIER_REQUEST:
                	processOFBarrierRequest(h, (OFMessage)m);
                	break;
                case STATS_REQUEST:
                	processOFStatsRequest(h, (OFMessage)m);
                	break;
                case FEATURES_REQUEST:
                	processOFFeaturesRequest(h, (OFMessage)m);
                	break;
                case FLOW_MOD:
                	processOFFlowMod(h, (OFMessage)m);
                	break;
                	
            }
        }

        /*-----------------------------------------------------------------
         * Default implementation for message handlers in any state.
         *
         * Individual states must override these if they want a behavior
         * that differs from the default.
         *
         * In general, these handlers simply ignore the message and do
         * nothing.
         *
         * There are some exceptions though, since some messages really
         * are handled the same way in every state (e.g., ECHO_REQUST) or
         * that are only valid in a single state (e.g., HELLO, GET_CONFIG_REPLY
         -----------------------------------------------------------------*/

        void processOFHello(OFControllerChannelHandler h, OFHello m) throws IOException {
            // we only expect hello in the WAIT_HELLO state
            illegalMessageReceived(h, m);
        }
        
        void processOFStatsRequest(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	h.proxy.toSwitch(m,null);
        }

        void processOFBarrierRequest(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	h.proxy.toSwitch(m, null );
        }
        
        void processOFFlowMod(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	h.proxy.toSwitch(m,null);
        }
        
        void processOFQueueGetRequest(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	OFGetConfigReply response = (OFGetConfigReply) BasicFactory.getInstance().getMessage(OFType.GET_CONFIG_REPLY);
        	response.setFlags((short)0);
        	response.setMissSendLength((short)65535);
            response.setXid(m.getXid());
        	h.sendMessage(response);
        }
        
        void processOFPacketOut(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	h.proxy.toSwitch(m, null);
        }
        
        void processSetConfig(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	OFGetConfigReply response = (OFGetConfigReply) BasicFactory.getInstance().getMessage(OFType.GET_CONFIG_REPLY);
        	response.setFlags((short)0);
        	response.setMissSendLength((short)65535);
        }
        
        void processOFVendor(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	log.debug("Handling OFVendor");
        	OFError errorResp = (OFError) BasicFactory.getInstance().getMessage(OFType.ERROR);
        	errorResp.setErrorCode((short)3);
        	errorResp.setErrorType((short)1);
        	errorResp.setOffendingMsg(m);
        	errorResp.setXid(m.getXid());
        	//do this to send nicira extensions
        	h.sendMessage(errorResp);
        }
        
        void processOFConfigRequest(OFControllerChannelHandler h, OFMessage m) throws IOException{
        	OFGetConfigReply response = (OFGetConfigReply) BasicFactory.getInstance().getMessage(OFType.GET_CONFIG_REPLY);
        	response.setFlags((short)0);
        	response.setMissSendLength((short)65535);
        	response.setXid(m.getXid());
        	h.sendMessage(response);
        }
        
        void processOFFeaturesRequest(OFControllerChannelHandler h, OFMessage  m)
                throws IOException {
        	log.debug("Have a features request... generating features reply.");
        	OFFeaturesReply response = (OFFeaturesReply) BasicFactory.getInstance().getMessage(OFType.FEATURES_REPLY);
        	response.setDatapathId(h.sw.getId());
        	response.setCapabilities(h.sw.getCapabilities());
        	response.setActions(h.sw.getActions());
        	//need to take the collection of ports and turn them into the list
        	List <OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        	for(ImmutablePort port : h.sw.getPorts()){
        		if(h.proxy.getSlicer().isPortPartOfSlice(port.getPortNumber())){
        			OFPhysicalPort p = port.toOFPhysicalPort();
        			ports.add(p);
        		}	
        	}
        	
        	response.setPorts(ports);
        	response.setBuffers(h.sw.getBuffers());
        	response.setTables(h.sw.getTables());
        	response.setXid(m.getXid());
        	log.debug("sending OFFeatureReply");
        	h.sendMessage(response);
        }
        
        void processOFBarrierReply(OFControllerChannelHandler h, OFBarrierReply m)
                throws IOException {
            // Silently ignore.
        }

        void processOFEchoRequest(OFControllerChannelHandler h, OFEchoRequest m)
            throws IOException {
            OFEchoReply reply = (OFEchoReply)
                    BasicFactory.getInstance().getMessage(OFType.ECHO_REPLY);
            reply.setXid(m.getXid());
            reply.setPayload(m.getPayload());
            reply.setLengthU(m.getLengthU());
            h.channel.write(Collections.singletonList(reply));
            log.debug("Sent ECHO_REPLY");
        }

        void processOFEchoReply(OFControllerChannelHandler h, OFEchoReply m)
            throws IOException {
            // Do nothing with EchoReplies !!
        }

        // no default implementation for OFError
        // every state must override it
        abstract void processOFError(OFControllerChannelHandler h, OFError m)
                throws IOException;


        void processOFFeaturesReply(OFControllerChannelHandler h, OFFeaturesReply  m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFFlowRemoved(OFControllerChannelHandler h, OFFlowRemoved m)
            throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFGetConfigReply(OFControllerChannelHandler h, OFGetConfigReply m)
                throws IOException {
            // we only expect config replies in the WAIT_CONFIG_REPLY state
            // TODO: might use two different strategies depending on whether
            // we got a miss length of 64k or not.
            illegalMessageReceived(h, m);
        }

        void processOFPacketIn(OFControllerChannelHandler h, OFPacketIn m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFQueueGetConfigReply(OFControllerChannelHandler h,
                                          OFQueueGetConfigReply m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFStatisticsReply(OFControllerChannelHandler h, OFStatisticsReply m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFVendor(OFControllerChannelHandler h, OFVendor m)
                throws IOException {
            // TODO: it might make sense to parse the vendor message here
            // into the known vendor messages we support and then call more
            // spefic event handlers
            unhandledMessageReceived(h, m);
        }
    }


    /**
     * Create a new unconnected OFChannelHandler.
     * 
     */
    OFControllerChannelHandler() {
        this.state = ChannelState.INIT;    
        
    }

    public void setSwitch(IOFSwitch sw){
    	this.sw = sw;
    }
    
    public void setProxy(Proxy proxy){
    	this.proxy = proxy;
    }
    
	/**
     * Is this a state in which the handshake has completed?
     * @return true if the handshake is complete
     */
    boolean isHandshakeComplete() {
        return this.state.isHandshakeComplete();
    }


  


    @Override
    @LogMessageDoc(message="New controller connection to {ip address}",
                   explanation="Connecting to a new controller")
    public void channelConnected(ChannelHandlerContext ctx,
                                 ChannelStateEvent e) throws Exception {
        
        channel = e.getChannel();
        log.info("New controller connection to {}",
                 channel.getRemoteAddress());
        log.debug("Sending HELLO");
        sendHandShakeMessage(OFType.HELLO);
        setState(ChannelState.WAIT_HELLO);
    }

    @Override
    @LogMessageDoc(message="Disconnected controller {switch information}",
                   explanation="The specified controller has disconnected.")
    public void channelDisconnected(ChannelHandlerContext ctx,
                                    ChannelStateEvent e) throws Exception {
        

    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Disconnecting switch {switch} due to read timeout",
                explanation="The connected switch has failed to send any " +
                            "messages or respond to echo requests",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
                message="Disconnecting switch {switch}: failed to " +
                        "complete handshake",
                explanation="The switch did not respond correctly " +
                            "to handshake messages",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
                message="Disconnecting switch {switch} due to IO Error: {}",
                explanation="There was an error communicating with the switch",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
                message="Disconnecting switch {switch} due to switch " +
                        "state error: {error}",
                explanation="The switch sent an unexpected message",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
                message="Disconnecting switch {switch} due to " +
                        "message parse failure",
                explanation="Could not parse a message from the switch",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
                message="Terminating controller due to storage exception",
                explanation="storage exception",//Controller.ERROR_DATABASE,
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Could not process message: queue full",
                explanation="OpenFlow messages are arriving faster than " +
                            " the controller can process them.",
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Error while processing message " +
                        "from switch {switch} {cause}",
                explanation="An error occurred processing the switch message",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        if (e.getCause() instanceof ReadTimeoutException) {
            // switch timeout
            log.error("Disconnecting slice {} from controller {} due to read timeout", this.proxy.getSlicer().getSliceName(),
                    this.proxy.getSlicer().getControllerAddress().toString());
           
            ctx.getChannel().close();
            
        } else if (e.getCause() instanceof HandshakeTimeoutException) {
            log.error("Disconnecting slice "+ this.proxy.getSlicer().getSliceName() +" after controller "+
            this.proxy.getSlicer().getControllerAddress().toString() + "failed to complete handshake");           
            ctx.getChannel().close();
        } else if (e.getCause() instanceof ClosedChannelException) {
            log.error("Channel for controller already closed");
        } else if (e.getCause() instanceof IOException) {
            log.error("Disconnecting  slice "+ this.proxy.getSlicer().getSliceName()+" controller "+
            this.proxy.getSlicer().getControllerAddress().toString() + " due to IO Error: {}", e.getCause().getMessage());
            if (log.isDebugEnabled()) {
                // still print stack trace if debug is enabled
                log.debug("StackTrace for previous Exception: ", e.getCause());
            }
            
            ctx.getChannel().close();
        } else if (e.getCause() instanceof SwitchStateException) {
            log.error("Disconnecting  slice "+ this.proxy.getSlicer().getSliceName()+" controller "+
            this.proxy.getSlicer().getControllerAddress().toString() + " due to switch state error: {}", this.proxy.getSlicer().getSliceName(),
                    e.getCause().getMessage());
            if (log.isDebugEnabled()) {
                // still print stack trace if debug is enabled
                log.debug("StackTrace for previous Exception: ", e.getCause());
            }
            
            ctx.getChannel().close();
        } else if (e.getCause() instanceof MessageParseException) {
            log.error("Disconnecting  slice "+ this.proxy.getSlicer().getSliceName()+" controller "+
            this.proxy.getSlicer().getControllerAddress().toString() + " due to message parse failure",
                                 e.getCause());
            
            ctx.getChannel().close();
        } else if (e.getCause() instanceof StorageException) {
            log.error("Terminating  slice "+ this.proxy.getSlicer().getSliceName()+" controller "+
            this.proxy.getSlicer().getControllerAddress().toString() + " due to storage exception",
                      e.getCause());
            //this.controller.terminate();
        } else if (e.getCause() instanceof RejectedExecutionException) {
            log.error("Could not process message: queue full");
            
        } else {
        	try{
	            log.error("Error while processing message from  slice "+ this.proxy.getSlicer().getSliceName()+" controller "+
	            this.proxy.getSlicer().getControllerAddress().toString() + " state " + this.state, e.getCause());
        	}catch(NullPointerException bad){
        		log.error("Something super crazy happened: " + bad.getMessage());
        		//damn something is fucked
        		ctx.getChannel().close();
        	}
            ctx.getChannel().close();
        }
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
            throws Exception {
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.ECHO_REQUEST);
        e.getChannel().write(Collections.singletonList(m));
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        if (e.getMessage() instanceof List) {
            @SuppressWarnings("unchecked")
            List<OFMessage> msglist = (List<OFMessage>)e.getMessage();

            for (OFMessage ofm : msglist) {
                
                try {
                                        // Do the actual packet processing
                    state.processOFMessage(this, ofm);

                }
                catch (Exception ex) {
                    // We are the last handler in the stream, so run the
                    // exception through the channel again by passing in
                    // ctx.getChannel().
                    Channels.fireExceptionCaught(ctx.getChannel(), ex);
                }
            }

           
            // Flush all thread local queues etc. generated by this train
            // of messages.
            
        }
        else {
            Channels.fireExceptionCaught(ctx.getChannel(),
                                         new AssertionError("Message received from Channel is not a list"));
        }
    }


    /**
     * Get a useable error string from the OFError.
     * @param error
     * @return
     */
    public static String getErrorString(OFError error) {
        // TODO: this really should be OFError.toString. Sigh.
        int etint = 0xffff & error.getErrorType();
        if (etint < 0 || etint >= OFErrorType.values().length) {
            return String.format("Unknown error type %d", etint);
        }
        OFErrorType et = OFErrorType.values()[etint];
        switch (et) {
            case OFPET_HELLO_FAILED:
                OFHelloFailedCode hfc =
                    OFHelloFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, hfc);
            case OFPET_BAD_REQUEST:
                OFBadRequestCode brc =
                    OFBadRequestCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, brc);
            case OFPET_BAD_ACTION:
                OFBadActionCode bac =
                    OFBadActionCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, bac);
            case OFPET_FLOW_MOD_FAILED:
                OFFlowModFailedCode fmfc =
                    OFFlowModFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, fmfc);
            case OFPET_PORT_MOD_FAILED:
                OFPortModFailedCode pmfc =
                    OFPortModFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, pmfc);
            case OFPET_QUEUE_OP_FAILED:
                OFQueueOpFailedCode qofc =
                    OFQueueOpFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, qofc);
            case OFPET_VENDOR_ERROR:
                // no codes known for vendor error
                return String.format("Error %s", et);
        }
        return null;
    }

    public void sendMessage(OFMessage m) throws IOException{
    	log.debug("attempting to send message: " + m.toString());
    	if(channel != null && channel.isConnected()){
    		channel.write(Collections.singletonList(m));
    	}else{
    		log.debug("Channel is not connected can not send message!!!");
    	}
    }
    
    @SuppressWarnings("unused")
	private void dispatchMessage(OFMessage m) throws IOException {
        // handleMessage will count
    	
    }



    /**
     * Update the channels state. Only called from the state machine.
     * TODO: enforce restricted state transitions
     * @param state
     */
    private void setState(ChannelState state) {
        this.state = state;
    }

    /**
     * Send a message to the switch using the handshake transactions ids.
     * @throws IOException
     */
    private void sendHandShakeMessage(OFType type) throws IOException {
        // Send initial Features Request
        OFMessage m = BasicFactory.getInstance().getMessage(type);
        m.setXid(handshakeTransactionIds--);
        channel.write(Collections.singletonList(m));
    }




}
