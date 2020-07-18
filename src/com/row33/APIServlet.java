package com.row33;

// Import required java libraries
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// Extend HttpServlet class
public class APIServlet extends HttpServlet {
	private static final long serialVersionUID = -3618382747850037838L;
	private static final Logger log = Logger.getLogger("com.row33.netonoff");

	public void init() throws ServletException {
		log.info("init servlet");
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.info("request start");
		String cmd = request.getRequestURI();
		if (!cmd.startsWith("/netonoff/api/")) {
			response.sendError(404);
		}
		cmd = cmd.replace("/netonoff/api/",  "");

		log.info(String.format("received API command: [%s]", cmd));
		
		JsonObject o = null;
		switch (cmd) {
		case "devices/list":
			o = listDevices(request, response);
			break;
		default:
			response.sendError(404);
		}
		
		if (o != null) {
			Gson g = new Gson();
			response.setContentType("application/json");
			PrintWriter out = response.getWriter();
			out.println(g.toJson(o));
		}
		
		log.info("request end");
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.info("request start");
		String cmd = request.getRequestURI();
		if (!cmd.startsWith("/netonoff/api/")) {
			response.sendError(404);
			log.info("request end");
			return;
		}
		
		cmd = cmd.replace("/netonoff/api/",  "");

		log.info(String.format("received API command: [%s]", cmd));
		JsonObject o = null;
		
		if (cmd.startsWith("devices/")) {
			cmd = cmd.replace("devices/", "");
			switch (cmd) {
			case "enable":
				enableDevices(request, response);
				o = listDevices(request, response);
				break;
			case "disable":
				disableDevices(request, response);
				o = listDevices(request, response);
				break;
			}
		}
		else if (cmd.startsWith("device/")) {
			cmd = cmd.replace("device/", "");
			o = deviceCmd(cmd, request, response);
		}
		
		if (o != null) {
			Gson g = new Gson();
			response.setContentType("application/json");
			PrintWriter out = response.getWriter();
			out.println(g.toJson(o));
		}
		else {
			response.sendError(404);
		}
		
		log.info("request end");
	}
	
	private JsonObject deviceCmd(String cmd, HttpServletRequest request, HttpServletResponse response) throws IOException {
		int idx = cmd.indexOf("/");
		if (idx < 0) {
			return null;
		}
		
		String sid = cmd.substring(0, idx);
		String scmd = cmd.substring(idx + 1);
		log.info(String.format("id %s scmd %s", sid, scmd));
		long id = -1;
		try {
			id = Integer.parseInt(sid);
		}
		catch (NumberFormatException ex) {
			return null;
		}
		
		JsonObject j = new JsonObject();
		
		InitialContext ctx = null;
		try {
			ctx = new InitialContext();
		}
		catch (NamingException ex) {
			j.addProperty("error",  ex.getMessage());
			return j;
		}

		DeviceInfo d = null;
		
		try (
			Connection c = connect(ctx);
		)
		{
			d = DeviceInfo.load(id, c);
			if (d == null) {
				return null;
			}
		}
		catch (SQLException | NamingException ex) {
			j.addProperty("error", ex.getMessage());
			return j;
		}
		
		switch (scmd) {
		case "enable":
			d.enable();
			break;
		case "disable":
			d.disable();
			break;
		default:
			return null;
		}
		
		return listDevices(request, response);
	}
	
	private JsonObject listDevices(HttpServletRequest request, HttpServletResponse response) throws IOException {
		JsonObject j = new JsonObject();
		
		InitialContext ctx = null;
		try {
			ctx = new InitialContext();
		}
		catch (NamingException ex) {
			j.addProperty("error",  ex.getMessage());
			return j;
		}

		try (
			Connection c = connect(ctx);
		)
		{
			List<DeviceInfo> dl = DeviceInfo.fetchAll(c);
			JsonArray jdl = new JsonArray();
			for (DeviceInfo d : dl) {
				JsonObject o = new JsonObject();
				o.addProperty("id",  d.getId());
				o.addProperty("name", d.getName());
				o.addProperty("enabled", d.isEnabled());
				jdl.add(o);
			}
			j.add("devices", jdl);
		}
		catch (SQLException | NamingException ex) {
			j.addProperty("error", ex.getMessage());
			return j;
		}
		
		return j;
	}
	
	private void enableDevices(HttpServletRequest request, HttpServletResponse response) {
		DeviceInfo.enableAll();
	}
	
	private void disableDevices(HttpServletRequest request, HttpServletResponse response) {
		InitialContext ctx = null;
		try {
			ctx = new InitialContext();
		}
		catch (NamingException ex) {
			log.warning(String.format("disable devices error: %s", ex));
			return;
		}

		try (
			Connection c = connect(ctx);
		)
		{
			List<DeviceInfo> dl = DeviceInfo.fetchAll(c);
			for (DeviceInfo d : dl) {
				d.disable();
			}
		}
		catch (SQLException | NamingException ex) {
			log.warning(String.format("disable devices error: %s", ex));
			return;
		}
	}
	
	private static Connection connect(InitialContext ctx) throws SQLException, NamingException {
		Connection conn = null;
		DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/sqlite/netonoff");
		conn = ds.getConnection();
		return conn;
	}
	
	public void destroy() {
		log.info("destroy servlet");
	}
}
