
/*
 * $Id: EchoServlet.java,v 1.5 2003/06/22 12:32:15 fukuda Exp $
 */
package org.mobicents.servlet.sip.example;

import java.util.*;
import java.io.IOException;

import javax.servlet.sip.SipServlet;	
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.ServletException;
import javax.servlet.sip.URI;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipFactory;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 */
public class Myapp extends SipServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static private Map<String, String> RegistrarDB;
	static private SipFactory factory;
	static private Map<String, String> UserStateDB = new HashMap<>();
	
	public Myapp() {
		super();
		RegistrarDB = new HashMap<String,String>();
	}
	
	public void init() {
		factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
	}

	/**
        * Acts as a registrar and location service for REGISTER messages
        * @param  request The SIP message received by the AS 
        */
	protected void doRegister(SipServletRequest request) throws ServletException,IOException {
		String aor = getSIPuri(request.getHeader("To"));
		String contact = getSIPuriPort(request.getHeader("Contact"));
		String domain = aor.substring(aor.indexOf("@")+1, aor.length());
        boolean deregister = request.getHeader("Contact").contains("expires=0");
		
		if (domain.equals("acme.pt")) { // Allow registration only for acme.pt domain


            if (deregister) {
                RegistrarDB.remove(aor); // Deregister user
				UserStateDB.remove(aor);
                System.out.println("User Deregistered: " + aor);
            } else {
                RegistrarDB.put(aor, contact); // Register user
				UserStateDB.put(aor, "DISPONIVEL");
                System.out.println("User Registered: " + aor);
            }

            SipServletResponse response = request.createResponse(200);
            response.send();
        } else {
            SipServletResponse response = request.createResponse(401); 
            response.send();
        }
				
	    // Some logs to show the content of the Registrar database.
		log("REGISTER (myapp):***");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		System.out.println(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("REGISTER (myapp):***");
	}

	/**
        * Sends SIP replies to INVITE messages
        * - 300 if registred
        * - 404 if not registred
        * @param  request The SIP message received by the AS 
        */
	protected void doInvite(SipServletRequest request) throws ServletException, IOException {
		
		// Some logs to show the content of the Registrar database.
		log("INVITE (myapp):***");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		System.out.println(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("INVITE (myapp):***");
		
		String fromAor = getSIPuri(request.getHeader("From")); // Get the From AoR
    	String fromDomain = fromAor.substring(fromAor.indexOf("@") + 1, fromAor.length());

		if (!fromDomain.equals("acme.pt")) {
			// If the request originator is not from acme.pt, send a response indicating service unavailability
			SipServletResponse response = request.createResponse(403); // Forbidden or another suitable response code
			response.send();
			return; 
		}
		
		// If the originator is from acme.pt, process the request
		String toAor = getSIPuri(request.getHeader("To")); // Get the To AoR
		
		if(toAor.equals("sip:chat@acme.pt")){
			//Conferencia
			Proxy proxy = request.getProxy();
			proxy.setRecordRoute(false);
			proxy.setSupervised(false);
			URI toContact = factory.createURI("sip:conf@127.0.0.1:5070");
			proxy.proxyTo(toContact);
			UserStateDB.put(fromAor, "CONFERENCIA");
		
		}else if(RegistrarDB.containsKey(toAor)) {
			// Chamada Direta
			// To AoR is registered, use a proxy to forward the request
			Proxy proxy = request.getProxy();
			proxy.setRecordRoute(false);
			proxy.setSupervised(false);
			URI toContact = factory.createURI(RegistrarDB.get(toAor));
			proxy.proxyTo(toContact);
			UserStateDB.put(fromAor, "OCUPADO");
    		UserStateDB.put(toAor, "OCUPADO");

		} else {
			// To AoR not registered, send 404 response
			SipServletResponse response = request.createResponse(404); // Not Found
			response.send();
		}
	}

	/**
    * Acts as a rmessage and location service for MESSAGE messages
    * @param  request The SIP message received by the AS 
    */
	protected void doMessage(SipServletRequest request) throws ServletException,IOException {
		String fromAor = getSIPuri(request.getHeader("From"));
		String fromDomain = fromAor.substring(fromAor.indexOf("@") + 1);

		// Check if the originator belongs to acme.pt
		if (!fromDomain.equals("acme.pt")) {
			SipServletResponse response = request.createResponse(403);
			response.send();
			return;
		}

		String toAor = getSIPuri(request.getHeader("To"));
		
		// Retrieve the target user from the message content
		String messageContent = request.getContent().toString();
		String targetUserAor = extractTargetAoR(messageContent);
		
		// Check if the message is for gofind@acme.pt
		if (!toAor.equals("sip:gofind@acme.pt") || targetUserAor.equals("INVALID")) {
			// Handle malformed request
			SipServletResponse response = request.createResponse(400); // Bad Request
			response.send();
			return;
		}

		// Check target user's status
		String targetUserStatus = UserStateDB.getOrDefault(targetUserAor, "NAO REGISTADO");

		// Respond based on target user's status
		SipServletResponse response = request.createResponse(200);
		response.setContent(targetUserStatus, "text/plain");
		response.send();

		// Additional logic for 3rd party call control if target user is available
		if (targetUserStatus.equals("DISPONIVEL")) {
			
			UserStateDB.put(fromAor, "OCUPADO");
        	UserStateDB.put(targetUserAor, "OCUPADO");
			
			initiateSipInvite(fromAor, targetUserAor);
		}
	}

	@Override
	protected void doBye(SipServletRequest req) throws ServletException, IOException {
		
		String fromAor = getSIPuri(req.getHeader("From"));
		String toAor = getSIPuri(req.getHeader("To"));

		// Update the state of the user who initiated the BYE
		UserStateDB.put(fromAor, "DISPONIVEL");

		
		if (toAor.equals("sip:chat@acme.pt")) {

			UserStateDB.put(fromAor, "DISPONIVEL");

		} else if (UserStateDB.getOrDefault(toAor, "").equals("OCUPADO")) {
			
			UserStateDB.put(fromAor, "DISPONIVEL");
			UserStateDB.put(toAor, "DISPONIVEL");

		} else {

			UserStateDB.put(fromAor, "DISPONIVEL");
			UserStateDB.put(toAor, "DISPONIVEL");
		}

		// Create and send an OK response to the BYE request
		SipServletResponse resp = req.createResponse(200);
		resp.send();

	}



	
	/**
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI 
        */
	protected String getSIPuri(String uri) {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		int indexCollon = f.indexOf(":", f.indexOf("@"));
		if (indexCollon != -1) {
			f = f.substring(0,indexCollon);
		}
		return f;
	}

	/**
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI and port 
        */
	protected String getSIPuriPort(String uri) {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		return f;
	}
	
	protected String extractTargetAoR(String messageContent) {
		String targetAoR = "INVALID";

		// Define a regular expression pattern for SIP URI (basic pattern)
		String sipUriPattern = "sip\\:[a-zA-Z0-9_.-]+@acme\\.pt";

		// Create a pattern object
		Pattern pattern = Pattern.compile(sipUriPattern);
		
		// Create a matcher object
		Matcher matcher = pattern.matcher(messageContent);
		
		if (matcher.find()) {
			// If a SIP URI is found in the message content
			targetAoR = matcher.group();
		}

		return targetAoR; // This will be "INVALID" if no AoR is found
	}	

	protected void initiateSipInvite(String fromAor, String targetAor) throws ServletException {
    try {
        SipServletRequest inviteRequest = factory.createRequest(
            factory.createApplicationSession(),
            "INVITE",
            factory.createAddress(fromAor),
            factory.createAddress(targetAor)
        );

        inviteRequest.send();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
}
